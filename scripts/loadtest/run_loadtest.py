#!/usr/bin/env python3
"""Load testing runner for Hittiguess core and AI microservices.

Simulates N concurrent games of 4 to 8 players performing authentication,
group management, audio check, game session lifecycle, STOMP WebSocket
connections, card placements, bets, and voice/song signaling.
Reports CPU, memory, active WebSockets, and operation latencies.
"""

import argparse
import asyncio
import base64
import json
import math
import os
import random
import socket
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Tuple

DEFAULT_GAMES_COUNT = 5
DEFAULT_PLAYERS_PER_GAME = 4
MINIMUM_PLAYERS_PER_GAME = 4
MAXIMUM_PLAYERS_PER_GAME = 8
DEFAULT_ROUNDS_COUNT = 3
DEFAULT_CORE_URL = "http://localhost:8080"
DEFAULT_AI_URL = "http://localhost:8000"
DEFAULT_HTTP_TIMEOUT_SECONDS = 15.0
DEFAULT_WEBSOCKET_TIMEOUT_SECONDS = 15.0

PERCENTILE_FIFTIETH = 50.0
PERCENTILE_NINETY_FIFTH = 95.0
PERCENTILE_NINETY_NINTH = 99.0
PERCENTAGE_MULTIPLIER = 100.0
MEGABYTE_DIVISOR = 1024.0 * 1024.0
MILLISECOND_MULTIPLIER = 1000.0

WEBSOCKET_MASKING_KEY_LENGTH = 4
WEBSOCKET_SHORT_PAYLOAD_LIMIT = 125
WEBSOCKET_MEDIUM_PAYLOAD_MARKER = 126
WEBSOCKET_LARGE_PAYLOAD_MARKER = 127
WEBSOCKET_FIN_AND_TEXT_OPCODE = 0x81
WEBSOCKET_MASK_BIT = 0x80

ACTION_AUTH_REGISTER = "auth_register"
ACTION_GROUP_CREATE = "group_create"
ACTION_GROUP_JOIN = "group_join"
ACTION_STOMP_CONNECT = "stomp_connect"
ACTION_AUDIO_CHECK_START = "audio_check_start"
ACTION_AUDIO_CHECK_STATUS = "audio_check_status"
ACTION_SESSION_START = "session_start"
ACTION_SESSION_LINK_OUT = "session_link_out"
ACTION_VOICE_SIGNALING = "voice_signaling"
ACTION_SONG_SIGNALING = "song_signaling"
ACTION_CARD_PLACEMENT = "card_placement"
ACTION_BET_PLACEMENT = "bet_placement"


@dataclass
class PlayerSession:
    player_index: int
    username: str
    email: str
    password: str
    user_id: Optional[int] = None
    access_token: Optional[str] = None
    reader: Optional[asyncio.StreamReader] = None
    writer: Optional[asyncio.StreamWriter] = None


@dataclass
class MetricsCollector:
    latencies_by_action: Dict[str, List[float]] = field(default_factory=dict)
    active_websockets: int = 0
    peak_websockets: int = 0
    total_actions_succeeded: int = 0
    total_actions_failed: int = 0
    error_messages: List[str] = field(default_factory=list)
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)

    async def record_action(self, action_name: str, duration_seconds: float, is_success: bool, error_detail: Optional[str] = None) -> None:
        async with self.lock:
            if is_success:
                self.total_actions_succeeded += 1
                if action_name not in self.latencies_by_action:
                    self.latencies_by_action[action_name] = []
                self.latencies_by_action[action_name].append(duration_seconds * MILLISECOND_MULTIPLIER)
            else:
                self.total_actions_failed += 1
                if error_detail:
                    self.error_messages.append(f"{action_name}: {error_detail}")

    async def increment_websockets(self) -> None:
        async with self.lock:
            self.active_websockets += 1
            if self.active_websockets > self.peak_websockets:
                self.peak_websockets = self.active_websockets

    async def decrement_websockets(self) -> None:
        async with self.lock:
            if self.active_websockets > 0:
                self.active_websockets -= 1


class StompWebSocketClient:
    """Lightweight RFC 6455 WebSocket and STOMP 1.2 client using pure asyncio."""

    def __init__(self, host: str, port: int, path: str, is_secure: bool, auth_token: str, metrics: MetricsCollector):
        self.host = host
        self.port = port
        self.path = path
        self.is_secure = is_secure
        self.auth_token = auth_token
        self.metrics = metrics
        self.reader: Optional[asyncio.StreamReader] = None
        self.writer: Optional[asyncio.StreamWriter] = None
        self.is_connected = False
        self.subscription_id_counter = 0

    async def connect(self) -> None:
        start_timestamp = time.perf_counter()
        try:
            ssl_context: Optional[ssl.SSLContext] = None
            if self.is_secure:
                ssl_context = ssl.create_default_context()

            self.reader, self.writer = await asyncio.wait_for(
                asyncio.open_connection(self.host, self.port, ssl=ssl_context),
                timeout=DEFAULT_WEBSOCKET_TIMEOUT_SECONDS
            )

            websocket_nonce = base64.b64encode(os.urandom(16)).decode("ascii")
            handshake_lines = [
                f"GET {self.path} HTTP/1.1",
                f"Host: {self.host}:{self.port}",
                "Upgrade: websocket",
                "Connection: Upgrade",
                f"Sec-WebSocket-Key: {websocket_nonce}",
                "Sec-WebSocket-Version: 13",
                f"Cookie: access_token={self.auth_token}",
                "\r\n"
            ]
            handshake_request = "\r\n".join(handshake_lines).encode("utf-8")
            self.writer.write(handshake_request)
            await self.writer.drain()

            handshake_response = await asyncio.wait_for(
                self.reader.readuntil(b"\r\n\r\n"),
                timeout=DEFAULT_WEBSOCKET_TIMEOUT_SECONDS
            )
            response_header_text = handshake_response.decode("utf-8", errors="replace")
            if " 101 " not in response_header_text:
                raise RuntimeError(f"WebSocket handshake failed: {response_header_text.splitlines()[0]}")

            stomp_connect_body = (
                f"CONNECT\naccept-version:1.1,1.2\nheart-beat:10000,10000\nAuthorization:Bearer {self.auth_token}\n\n\x00"
            )
            await self._send_text_frame(stomp_connect_body)

            connected_frame = await self._read_stomp_frame()
            if not connected_frame.startswith("CONNECTED"):
                raise RuntimeError(f"STOMP connection failed: {connected_frame}")

            self.is_connected = True
            await self.metrics.increment_websockets()
            duration = time.perf_counter() - start_timestamp
            await self.metrics.record_action(ACTION_STOMP_CONNECT, duration, True)
        except Exception as connection_error:
            duration = time.perf_counter() - start_timestamp
            await self.metrics.record_action(ACTION_STOMP_CONNECT, duration, False, str(connection_error))
            await self.close()
            raise connection_error

    async def subscribe(self, destination: str) -> str:
        self.subscription_id_counter += 1
        sub_identifier = f"sub-{self.subscription_id_counter}"
        frame_text = f"SUBSCRIBE\nid:{sub_identifier}\ndestination:{destination}\n\n\x00"
        await self._send_text_frame(frame_text)
        return sub_identifier

    async def send(self, destination: str, payload_json_string: str) -> None:
        frame_text = (
            f"SEND\ndestination:{destination}\ncontent-type:application/json\n\n{payload_json_string}\x00"
        )
        await self._send_text_frame(frame_text)

    async def close(self) -> None:
        if self.is_connected:
            self.is_connected = False
            await self.metrics.decrement_websockets()
        if self.writer is not None:
            try:
                self.writer.close()
                await self.writer.wait_closed()
            except Exception:
                pass
            self.writer = None
            self.reader = None

    async def _send_text_frame(self, text_content: str) -> None:
        if self.writer is None:
            raise RuntimeError("WebSocket writer is not open")
        payload_bytes = text_content.encode("utf-8")
        payload_length = len(payload_bytes)

        header_bytes = bytearray()
        header_bytes.append(WEBSOCKET_FIN_AND_TEXT_OPCODE)

        if payload_length <= WEBSOCKET_SHORT_PAYLOAD_LIMIT:
            header_bytes.append(WEBSOCKET_MASK_BIT | payload_length)
        elif payload_length <= 0xFFFF:
            header_bytes.append(WEBSOCKET_MASK_BIT | WEBSOCKET_MEDIUM_PAYLOAD_MARKER)
            header_bytes.extend(payload_length.to_bytes(2, byteorder="big"))
        else:
            header_bytes.append(WEBSOCKET_MASK_BIT | WEBSOCKET_LARGE_PAYLOAD_MARKER)
            header_bytes.extend(payload_length.to_bytes(8, byteorder="big"))

        masking_key = os.urandom(WEBSOCKET_MASKING_KEY_LENGTH)
        header_bytes.extend(masking_key)

        masked_payload = bytearray(payload_length)
        for byte_position in range(payload_length):
            masked_payload[byte_position] = payload_bytes[byte_position] ^ masking_key[byte_position % WEBSOCKET_MASKING_KEY_LENGTH]

        self.writer.write(header_bytes + masked_payload)
        await self.writer.drain()

    async def _read_stomp_frame(self) -> str:
        if self.reader is None:
            raise RuntimeError("WebSocket reader is not open")
        accumulated_text = ""
        while True:
            two_bytes = await asyncio.wait_for(self.reader.readexactly(2), timeout=DEFAULT_WEBSOCKET_TIMEOUT_SECONDS)
            second_byte = two_bytes[1]
            payload_length = second_byte & 0x7F

            if payload_length == WEBSOCKET_MEDIUM_PAYLOAD_MARKER:
                length_bytes = await self.reader.readexactly(2)
                payload_length = int.from_bytes(length_bytes, byteorder="big")
            elif payload_length == WEBSOCKET_LARGE_PAYLOAD_MARKER:
                length_bytes = await self.reader.readexactly(8)
                payload_length = int.from_bytes(length_bytes, byteorder="big")

            payload_data = await self.reader.readexactly(payload_length)
            decoded_chunk = payload_data.decode("utf-8", errors="replace")
            accumulated_text += decoded_chunk
            if "\x00" in accumulated_text:
                return accumulated_text.split("\x00")[0]


def synchronous_http_request(
    method: str,
    url: str,
    payload_dictionary: Optional[Dict[str, Any]] = None,
    auth_token: Optional[str] = None,
    timeout_seconds: float = DEFAULT_HTTP_TIMEOUT_SECONDS
) -> Tuple[int, Dict[str, Any]]:
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if auth_token:
        headers["Authorization"] = f"Bearer {auth_token}"

    encoded_data: Optional[bytes] = None
    if payload_dictionary is not None:
        encoded_data = json.dumps(payload_dictionary).encode("utf-8")

    request_object = urllib.request.Request(url, data=encoded_data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request_object, timeout=timeout_seconds) as response:
            status_code = response.getcode()
            response_bytes = response.read()
            if not response_bytes:
                return status_code, {}
            return status_code, json.loads(response_bytes.decode("utf-8"))
    except urllib.error.HTTPError as http_error:
        error_body = http_error.read().decode("utf-8", errors="replace")
        parsed_body: Dict[str, Any] = {}
        try:
            parsed_body = json.loads(error_body)
        except Exception:
            parsed_body = {"raw": error_body}
        return http_error.code, parsed_body
    except Exception as network_error:
        return 0, {"error": str(network_error)}


async def execute_http_call(
    metrics: MetricsCollector,
    action_name: str,
    method: str,
    url: str,
    payload_dictionary: Optional[Dict[str, Any]] = None,
    auth_token: Optional[str] = None,
    expected_status_codes: Tuple[int, ...] = (200, 201, 204)
) -> Tuple[int, Dict[str, Any]]:
    start_time = time.perf_counter()
    loop = asyncio.get_running_loop()
    status_code, body = await loop.run_in_executor(
        None, synchronous_http_request, method, url, payload_dictionary, auth_token, DEFAULT_HTTP_TIMEOUT_SECONDS
    )
    duration = time.perf_counter() - start_time
    is_success = status_code in expected_status_codes
    error_detail = None if is_success else f"HTTP {status_code}: {json.dumps(body)}"
    await metrics.record_action(action_name, duration, is_success, error_detail)
    return status_code, body


async def simulate_single_game(
    game_identifier: int,
    players_count: int,
    rounds_count: int,
    core_url: str,
    metrics: MetricsCollector
) -> None:
    timestamp_marker = int(time.time())
    players: List[PlayerSession] = []
    stomp_clients: List[StompWebSocketClient] = []

    parsed_core = urllib.parse.urlparse(core_url)
    websocket_host = parsed_core.hostname or "localhost"
    websocket_port = parsed_core.port or (443 if parsed_core.scheme == "https" else 80)
    is_secure_websocket = parsed_core.scheme == "https"

    try:
        # Step 1: Authentication for all players in this game
        for player_index in range(players_count):
            unique_name = f"load_{game_identifier}_{player_index}_{timestamp_marker}"
            player_session = PlayerSession(
                player_index=player_index,
                username=unique_name,
                email=f"{unique_name}@example.com",
                password="LoadtestPassword123!"
            )
            registration_payload = {
                "username": player_session.username,
                "email": player_session.email,
                "password": player_session.password
            }
            status_code, response_body = await execute_http_call(
                metrics,
                ACTION_AUTH_REGISTER,
                "POST",
                f"{core_url}/api/auth/register",
                registration_payload,
                expected_status_codes=(200, 201)
            )
            if status_code not in (200, 201):
                return
            player_session.access_token = response_body.get("accessToken")
            player_session.user_id = response_body.get("user", {}).get("id")
            players.append(player_session)

        admin_player = players[0]

        # Step 2: Group Creation and Membership Join
        create_payload = {"shouldReplaceCurrentGroup": True}
        status_code, group_response = await execute_http_call(
            metrics,
            ACTION_GROUP_CREATE,
            "POST",
            f"{core_url}/api/groups",
            create_payload,
            auth_token=admin_player.access_token,
            expected_status_codes=(200, 201)
        )
        if status_code not in (200, 201):
            return

        group_id = group_response.get("id")
        join_code = group_response.get("joinCode")

        for non_admin_player in players[1:]:
            status_code, unused_join_response = await execute_http_call(
                metrics,
                ACTION_GROUP_JOIN,
                "POST",
                f"{core_url}/api/groups/join/{join_code}",
                auth_token=non_admin_player.access_token,
                expected_status_codes=(200, 201)
            )
            if status_code not in (200, 201):
                return

        # Step 3: Establish WebSocket and STOMP Connections
        for player in players:
            client = StompWebSocketClient(
                host=websocket_host,
                port=websocket_port,
                path="/ws",
                is_secure=is_secure_websocket,
                auth_token=player.access_token or "",
                metrics=metrics
            )
            await client.connect()
            await client.subscribe(f"/topic/groups/{group_id}")
            await client.subscribe(f"/topic/groups/{group_id}/voice")
            await client.subscribe(f"/topic/groups/{group_id}/song")
            stomp_clients.append(client)

        # Step 4: Voice Signaling Exchange Simulation
        for sender_index in range(min(2, len(players))):
            target_index = (sender_index + 1) % len(players)
            voice_signal_payload = {
                "type": "OFFER",
                "targetMemberUserId": players[target_index].user_id,
                "payload": "{\"sdp\":\"fake-sdp-offer\"}"
            }
            start_signal_time = time.perf_counter()
            try:
                await stomp_clients[sender_index].send(
                    f"/app/groups/{group_id}/voice/signal",
                    json.dumps(voice_signal_payload)
                )
                signal_duration = time.perf_counter() - start_signal_time
                await metrics.record_action(ACTION_VOICE_SIGNALING, signal_duration, True)
            except Exception as signal_exception:
                signal_duration = time.perf_counter() - start_signal_time
                await metrics.record_action(ACTION_VOICE_SIGNALING, signal_duration, False, str(signal_exception))

        # Step 5: Audio Check Initiation and Completion
        status_code, unused_audio_check_response = await execute_http_call(
            metrics,
            ACTION_AUDIO_CHECK_START,
            "POST",
            f"{core_url}/api/groups/{group_id}/start-session",
            auth_token=admin_player.access_token,
            expected_status_codes=(200, 503)
        )
        if status_code == 503:
            # Server capacity admission limit was reached
            return
        if status_code != 200:
            return

        for player in players:
            status_report_payload = {"status": "CAN_SHARE"}
            await execute_http_call(
                metrics,
                ACTION_AUDIO_CHECK_STATUS,
                "POST",
                f"{core_url}/api/groups/{group_id}/audio-check/status",
                status_report_payload,
                auth_token=player.access_token
            )

        status_code, unused_start_response = await execute_http_call(
            metrics,
            ACTION_SESSION_START,
            "POST",
            f"{core_url}/api/groups/{group_id}/audio-check/start",
            auth_token=admin_player.access_token,
            expected_status_codes=(200, 503)
        )
        if status_code != 200:
            return

        # Query Active Session Identifier
        status_code, session_data = await execute_http_call(
            metrics,
            "session_query_active",
            "GET",
            f"{core_url}/api/sessions/groups/{group_id}/active",
            auth_token=admin_player.access_token
        )
        session_id = session_data.get("id")
        if not session_id:
            return

        for client in stomp_clients:
            await client.subscribe(f"/topic/sessions/{session_id}")

        # Step 6: Gameplay Rounds (Link-Out, Placement, Betting, Song Signaling)
        for round_number in range(rounds_count):
            # DJ Link-Out Query
            await execute_http_call(
                metrics,
                ACTION_SESSION_LINK_OUT,
                "GET",
                f"{core_url}/api/sessions/{session_id}/link-out",
                auth_token=admin_player.access_token,
                expected_status_codes=(200, 403, 404)
            )

            # Song WebRTC Signaling
            target_player_id = players[1].user_id if len(players) > 1 else admin_player.user_id
            song_signal_payload = {
                "type": "OFFER",
                "targetMemberUserId": target_player_id,
                "payload": "{\"sdp\":\"song-mesh-offer\"}"
            }
            start_song_signal = time.perf_counter()
            try:
                await stomp_clients[0].send(
                    f"/app/sessions/{session_id}/song/signal",
                    json.dumps(song_signal_payload)
                )
                song_duration = time.perf_counter() - start_song_signal
                await metrics.record_action(ACTION_SONG_SIGNALING, song_duration, True)
            except Exception as song_exception:
                song_duration = time.perf_counter() - start_song_signal
                await metrics.record_action(ACTION_SONG_SIGNALING, song_duration, False, str(song_exception))

            # Active Player Card Placement
            placement_payload = {"position": round_number}
            start_placement = time.perf_counter()
            try:
                await stomp_clients[0].send(
                    f"/app/sessions/{session_id}/place",
                    json.dumps(placement_payload)
                )
                placement_duration = time.perf_counter() - start_placement
                await metrics.record_action(ACTION_CARD_PLACEMENT, placement_duration, True)
            except Exception as placement_exception:
                placement_duration = time.perf_counter() - start_placement
                await metrics.record_action(ACTION_CARD_PLACEMENT, placement_duration, False, str(placement_exception))

            # Other Players Betting
            for better_index in range(1, len(players)):
                bet_payload = {"position": round_number}
                start_bet = time.perf_counter()
                try:
                    await stomp_clients[better_index].send(
                        f"/app/sessions/{session_id}/bet",
                        json.dumps(bet_payload)
                    )
                    bet_duration = time.perf_counter() - start_bet
                    await metrics.record_action(ACTION_BET_PLACEMENT, bet_duration, True)
                except Exception as bet_exception:
                    bet_duration = time.perf_counter() - start_bet
                    await metrics.record_action(ACTION_BET_PLACEMENT, bet_duration, False, str(bet_exception))

            await asyncio.sleep(0.1)

    finally:
        for client in stomp_clients:
            await client.close()


def query_system_metrics(core_url: str, ai_url: str) -> Dict[str, Any]:
    metrics_result: Dict[str, Any] = {
        "core_cpu_percent": "N/A",
        "core_memory_used_mb": "N/A",
        "core_memory_max_mb": "N/A",
        "ai_status": "N/A"
    }

    status_code, cpu_body = synchronous_http_request("GET", f"{core_url}/actuator/metrics/process.cpu.usage")
    if status_code == 200:
        measurements = cpu_body.get("measurements", [])
        if measurements:
            cpu_ratio = measurements[0].get("value", 0.0)
            metrics_result["core_cpu_percent"] = f"{cpu_ratio * PERCENTAGE_MULTIPLIER:.2f}%"

    status_code, memory_body = synchronous_http_request("GET", f"{core_url}/actuator/metrics/jvm.memory.used")
    if status_code == 200:
        measurements = memory_body.get("measurements", [])
        if measurements:
            bytes_used = measurements[0].get("value", 0.0)
            metrics_result["core_memory_used_mb"] = f"{bytes_used / MEGABYTE_DIVISOR:.1f} MB"

    status_code, max_memory_body = synchronous_http_request("GET", f"{core_url}/actuator/metrics/jvm.memory.max")
    if status_code == 200:
        measurements = max_memory_body.get("measurements", [])
        if measurements:
            bytes_max = measurements[0].get("value", 0.0)
            metrics_result["core_memory_max_mb"] = f"{bytes_max / MEGABYTE_DIVISOR:.1f} MB"

    status_code, ai_health = synchronous_http_request("GET", f"{ai_url}/health")
    if status_code == 200:
        metrics_result["ai_status"] = ai_health.get("status", "healthy")
    else:
        metrics_result["ai_status"] = "offline_or_unreachable"

    return metrics_result


def calculate_percentile(sorted_values: List[float], percentile: float) -> float:
    if not sorted_values:
        return 0.0
    index_position = (len(sorted_values) - 1) * (percentile / 100.0)
    lower_index = math.floor(index_position)
    upper_index = math.ceil(index_position)
    if lower_index == upper_index:
        return sorted_values[int(index_position)]
    lower_value = sorted_values[lower_index]
    upper_value = sorted_values[upper_index]
    return lower_value + (upper_value - lower_value) * (index_position - lower_index)


def print_summary_report(
    games_count: int,
    players_per_game: int,
    duration_seconds: float,
    metrics: MetricsCollector,
    system_metrics: Dict[str, Any]
) -> Dict[str, Any]:
    total_players = games_count * players_per_game
    header_separator = "=" * 80
    section_separator = "-" * 80

    print(header_separator)
    print("HITTIGUESS LOAD TEST SUMMARY REPORT")
    print(header_separator)
    print(f"Concurrent Games:      {games_count}")
    print(f"Players Per Game:      {players_per_game}")
    print(f"Total Simulated Users: {total_players}")
    print(f"Test Execution Time:   {duration_seconds:.2f} seconds")
    print(f"Peak Active WebSockets:{metrics.peak_websockets}")
    print(f"Actions Succeeded:     {metrics.total_actions_succeeded}")
    print(f"Actions Failed:        {metrics.total_actions_failed}")
    print(section_separator)
    print("SYSTEM RESOURCE UTILIZATION (ACTUATOR & HEALTH):")
    print(f"Core CPU Usage:        {system_metrics['core_cpu_percent']}")
    print(f"Core Memory Used:      {system_metrics['core_memory_used_mb']} / {system_metrics['core_memory_max_mb']}")
    print(f"AI Service Health:     {system_metrics['ai_status']}")
    print(section_separator)
    print(f"{'Game Action':<24} {'Count':<7} {'Min(ms)':<9} {'Mean(ms)':<9} {'p50(ms)':<9} {'p95(ms)':<9} {'p99(ms)':<9} {'Max(ms)':<9}")
    print(section_separator)

    actions_summary: Dict[str, Any] = {}
    for action_name, latencies in sorted(metrics.latencies_by_action.items()):
        sorted_latencies = sorted(latencies)
        action_count = len(sorted_latencies)
        minimum_latency = sorted_latencies[0]
        maximum_latency = sorted_latencies[-1]
        mean_latency = sum(sorted_latencies) / action_count
        p50_latency = calculate_percentile(sorted_latencies, PERCENTILE_FIFTIETH)
        p95_latency = calculate_percentile(sorted_latencies, PERCENTILE_NINETY_FIFTH)
        p99_latency = calculate_percentile(sorted_latencies, PERCENTILE_NINETY_NINTH)

        actions_summary[action_name] = {
            "count": action_count,
            "min_ms": round(minimum_latency, 2),
            "mean_ms": round(mean_latency, 2),
            "p50_ms": round(p50_latency, 2),
            "p95_ms": round(p95_latency, 2),
            "p99_ms": round(p99_latency, 2),
            "max_ms": round(maximum_latency, 2)
        }

        print(
            f"{action_name:<24} {action_count:<7} {minimum_latency:<9.1f} {mean_latency:<9.1f} "
            f"{p50_latency:<9.1f} {p95_latency:<9.1f} {p99_latency:<9.1f} {maximum_latency:<9.1f}"
        )

    print(header_separator)
    print("CONTAINER SIZING & ADMISSION CAPACITY EVALUATION:")
    print("Core Container App Limit:  1.0 vCPU, 2.0 GiB RAM (defined in scripts/azure/create-container-apps.ps1)")
    print("AI Container App Limit:    0.5 vCPU, 1.0 GiB RAM")
    print("Core Admission Ceiling:    50 concurrent active games (returns HTTP 503 when full)")
    print(header_separator)

    return {
        "concurrency": {
            "games_count": games_count,
            "players_per_game": players_per_game,
            "total_players": total_players
        },
        "duration_seconds": duration_seconds,
        "active_websockets_peak": metrics.peak_websockets,
        "actions_succeeded": metrics.total_actions_succeeded,
        "actions_failed": metrics.total_actions_failed,
        "system_metrics": system_metrics,
        "actions": actions_summary
    }


async def main_async() -> int:
    parser = argparse.ArgumentParser(description="Hittiguess core and AI load test runner")
    parser.add_argument("--games", type=int, default=DEFAULT_GAMES_COUNT, help="Number of concurrent games")
    parser.add_argument(
        "--players-per-game",
        type=int,
        default=DEFAULT_PLAYERS_PER_GAME,
        help="Players per game (4 to 8)"
    )
    parser.add_argument("--rounds", type=int, default=DEFAULT_ROUNDS_COUNT, help="Gameplay rounds to simulate")
    parser.add_argument("--core-url", type=str, default=DEFAULT_CORE_URL, help="Base URL of hittiguess-core")
    parser.add_argument("--ai-url", type=str, default=DEFAULT_AI_URL, help="Base URL of hittiguess-ai")
    parser.add_argument("--report-json", type=str, default=None, help="Optional output JSON report path")
    parser.add_argument(
        "--mock-dry-run",
        action="store_true",
        help="Simulate metrics and client validation without running against live servers"
    )

    arguments = parser.parse_args()

    if arguments.players_per_game < MINIMUM_PLAYERS_PER_GAME or arguments.players_per_game > MAXIMUM_PLAYERS_PER_GAME:
        print(f"Error: --players-per-game must be between {MINIMUM_PLAYERS_PER_GAME} and {MAXIMUM_PLAYERS_PER_GAME}")
        return 1

    metrics = MetricsCollector()
    start_time = time.perf_counter()

    if arguments.mock_dry_run:
        print("Executing dry-run client simulation...")
        for game_index in range(arguments.games):
            for player_index in range(arguments.players_per_game):
                await metrics.record_action(ACTION_AUTH_REGISTER, random.uniform(0.015, 0.045), True)
            await metrics.record_action(ACTION_GROUP_CREATE, random.uniform(0.020, 0.050), True)
            for join_iteration in range(arguments.players_per_game - 1):
                await metrics.record_action(ACTION_GROUP_JOIN, random.uniform(0.010, 0.030), True)
            for connect_iteration in range(arguments.players_per_game):
                await metrics.increment_websockets()
                await metrics.record_action(ACTION_STOMP_CONNECT, random.uniform(0.005, 0.015), True)
            await metrics.record_action(ACTION_AUDIO_CHECK_START, random.uniform(0.015, 0.035), True)
            for connect_iteration in range(arguments.players_per_game):
                await metrics.record_action(ACTION_AUDIO_CHECK_STATUS, random.uniform(0.008, 0.025), True)
            await metrics.record_action(ACTION_SESSION_START, random.uniform(0.030, 0.080), True)
            for round_idx in range(arguments.rounds):
                await metrics.record_action(ACTION_SESSION_LINK_OUT, random.uniform(0.005, 0.012), True)
                await metrics.record_action(ACTION_VOICE_SIGNALING, random.uniform(0.002, 0.008), True)
                await metrics.record_action(ACTION_SONG_SIGNALING, random.uniform(0.002, 0.008), True)
                await metrics.record_action(ACTION_CARD_PLACEMENT, random.uniform(0.004, 0.015), True)
                for join_iteration in range(arguments.players_per_game - 1):
                    await metrics.record_action(ACTION_BET_PLACEMENT, random.uniform(0.003, 0.012), True)
        system_metrics = {
            "core_cpu_percent": "14.20%",
            "core_memory_used_mb": "412.5 MB",
            "core_memory_max_mb": "2048.0 MB",
            "ai_status": "ok"
        }
    else:
        game_tasks = [
            simulate_single_game(
                game_index,
                arguments.players_per_game,
                arguments.rounds,
                arguments.core_url,
                metrics
            )
            for game_index in range(arguments.games)
        ]
        await asyncio.gather(*game_tasks, return_exceptions=True)
        system_metrics = query_system_metrics(arguments.core_url, arguments.ai_url)

    duration = time.perf_counter() - start_time
    report_data = print_summary_report(
        arguments.games,
        arguments.players_per_game,
        duration,
        metrics,
        system_metrics
    )

    if arguments.report_json:
        with open(arguments.report_json, "w", encoding="utf-8") as file_handle:
            json.dump(report_data, file_handle, indent=2)
        print(f"Detailed JSON report saved to: {arguments.report_json}")

    return 0


def main() -> None:
    exit_code = asyncio.run(main_async())
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
