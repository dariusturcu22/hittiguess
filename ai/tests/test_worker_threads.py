import time

import anyio
import anyio.to_thread
import httpx
from fastapi.testclient import TestClient

from app.auth import INTERNAL_API_KEY_HEADER
from app.config import settings
from app.main import WORKER_THREAD_LIMIT, app
from app.metadata import service

CONCURRENT_CALLS = 6
SIMULATED_SOURCE_SECONDS = 0.3
# Six serial calls take 1.8 seconds; parallel ones finish well under half of that.
PARALLEL_BOUND_SECONDS = CONCURRENT_CALLS * SIMULATED_SOURCE_SECONDS / 2
HTTP_OK = 200
SOURCE_BODY = {"title": "Test Song", "artists": ["Test Artist"], "youtubeId": "abcdefghijk"}


def test_startup_raises_the_worker_thread_limit():
    with TestClient(app) as client:
        total_tokens = client.portal.call(lambda: anyio.to_thread.current_default_thread_limiter().total_tokens)

    assert total_tokens == WORKER_THREAD_LIMIT


def test_slow_stage_calls_are_served_at_the_same_time(mocker):
    def slow_source(source_name, title, artists, *, raise_on_failure):
        time.sleep(SIMULATED_SOURCE_SECONDS)
        return service.SourceStageResult(candidates=[], earliest_year=None, sitelinks_count=None)

    mocker.patch.object(service, "run_structured_source_stage", side_effect=slow_source)
    headers = {INTERNAL_API_KEY_HEADER: settings.internal_service_api_key}
    statuses = []

    async def call_concurrently():
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://ai.test") as client:

            async def call_once():
                response = await client.post("/stages/musicbrainz", json=SOURCE_BODY, headers=headers)
                statuses.append(response.status_code)

            async with anyio.create_task_group() as task_group:
                for call_number in range(CONCURRENT_CALLS):
                    task_group.start_soon(call_once)

    started_at = time.monotonic()
    anyio.run(call_concurrently)
    elapsed_seconds = time.monotonic() - started_at

    assert statuses == [HTTP_OK] * CONCURRENT_CALLS
    assert elapsed_seconds < PARALLEL_BOUND_SECONDS
