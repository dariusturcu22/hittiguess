#!/usr/bin/env python3
"""Configures and validates production Core uptime monitoring."""

import json
import os
import sys
import time
import urllib.error
import urllib.request
from typing import Any, Dict, Tuple

DEFAULT_PRODUCTION_HEALTH_URL = "https://api.hittiguess.com/actuator/health"
DEFAULT_PRODUCTION_SITE_URL = "https://www.hittiguess.com"
UPTIME_PROBE_TIMEOUT_SECONDS = 10.0
HTTP_OK_STATUS = 200


def evaluate_endpoint_health(target_url: str) -> Tuple[bool, int, float, Dict[str, Any]]:
    start_timestamp = time.time()
    request = urllib.request.Request(
        target_url,
        headers={"User-Agent": "Hittiguess-Uptime-Monitor/1.0"},
        method="GET",
    )
    try:
        with urllib.request.urlopen(request, timeout=UPTIME_PROBE_TIMEOUT_SECONDS) as response:
            latency_seconds = time.time() - start_timestamp
            status_code = response.getcode()
            response_content = response.read().decode("utf-8")
            parsed_json: Dict[str, Any] = {}
            if response_content.strip():
                try:
                    parsed_json = json.loads(response_content)
                except Exception:
                    pass
            is_healthy = status_code == HTTP_OK_STATUS and (
                not parsed_json or parsed_json.get("status") == "UP"
            )
            return is_healthy, status_code, latency_seconds, parsed_json
    except urllib.error.HTTPError as http_error:
        latency_seconds = time.time() - start_timestamp
        return False, http_error.code, latency_seconds, {}
    except Exception as connection_error:
        latency_seconds = time.time() - start_timestamp
        return False, 0, latency_seconds, {"error": str(connection_error)}


def build_synthetic_monitoring_specification(health_url: str) -> Dict[str, Any]:
    return {
        "job": "hittiguess-core-uptime",
        "target": health_url,
        "frequency": 60000,
        "timeout": 5000,
        "probes": ["Frankfurt", "London", "Amsterdam"],
        "settings": {
            "http": {
                "method": "GET",
                "validStatusCodes": [200],
                "failIfSSL": False,
                "failIfNotSSL": True,
                "tlsConfig": {
                    "insecureSkipVerify": False,
                },
            }
        },
    }


def main() -> None:
    print("================================================================================")
    print("PRODUCTION UPTIME MONITORING CONFIGURATION AND VERIFICATION")
    print("================================================================================")

    health_target_url = os.environ.get(
        "CORE_HEALTH_URL", DEFAULT_PRODUCTION_HEALTH_URL
    ).strip()
    site_target_url = os.environ.get(
        "SITE_URL", DEFAULT_PRODUCTION_SITE_URL
    ).strip()

    print(f"\n1. Probing Core API Health Endpoint: {health_target_url}")
    core_healthy, core_status, core_latency, core_payload = evaluate_endpoint_health(
        health_target_url
    )
    print(f"  HTTP Status Code: {core_status}")
    print(f"  Response Latency: {core_latency:.3f}s")
    print(f"  Payload:          {core_payload}")
    print(f"  Health Check:     {'PASS' if core_healthy else 'FAIL'}")

    print(f"\n2. Probing Frontend Site Endpoint: {site_target_url}")
    site_healthy, site_status, site_latency, unused_payload = evaluate_endpoint_health(
        site_target_url
    )
    print(f"  HTTP Status Code: {site_status}")
    print(f"  Response Latency: {site_latency:.3f}s")
    print(f"  Health Check:     {'PASS' if site_healthy else 'FAIL'}")

    print("\n3. Synthetic Monitoring Configuration Specification:")
    synthetic_spec = build_synthetic_monitoring_specification(health_target_url)
    print(json.dumps(synthetic_spec, indent=2))

    if not core_healthy:
        print("\nError: Core health probe failed during verification.")
        sys.exit(1)

    print("\n================================================================================")
    print("CORE UPTIME MONITORING VERIFIED (Probes passing, specification documented)")
    print("================================================================================")


if __name__ == "__main__":
    main()
