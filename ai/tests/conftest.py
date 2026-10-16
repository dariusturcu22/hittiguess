import os

os.environ.setdefault("OPENAI_API_KEY", "test-openai-key")
os.environ.setdefault("YOUTUBE_API_KEY", "test-youtube-key")
os.environ.setdefault("DISCOGS_CONSUMER_KEY", "test-discogs-key")
os.environ.setdefault("DISCOGS_CONSUMER_SECRET", "test-discogs-secret")
os.environ.setdefault("INTERNAL_SERVICE_API_KEY", "test-internal-key")
os.environ.setdefault("DEEPINFRA_API_KEY", "test-deepinfra-key")
os.environ.setdefault("DATABASE_URL", "postgresql://test:test@localhost:5432/test")
# An empty DSN keeps importing the app from initializing Sentry with a local .env's DSN.
os.environ.setdefault("SENTRY_DSN", "")

import pytest

from app.metadata.sources import pace_state
from app.usage import recorder


class DiscardingUsageWriter:
    def submit(self, function, *arguments):
        return None


@pytest.fixture(autouse=True)
def discard_api_usage_writes(monkeypatch):
    """Keeps every test from opening a database connection to record API usage."""
    monkeypatch.setattr(recorder, "_usage_writer", DiscardingUsageWriter())


@pytest.fixture(autouse=True)
def pace_sources_in_memory(monkeypatch):
    """Keeps every source pacer off the database, pacing in memory as it does when
    stage_pace is unavailable. Tests of the persisted pace state set their own store."""
    monkeypatch.setattr(pace_state, "shared_store", None)
