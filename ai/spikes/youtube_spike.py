"""Spike: YouTube Data API, kept for side-by-side comparison against the
structured sources, the real source lives at app/metadata/sources/youtube.py
already. See spikes/README.md.

Usage: python spikes/youtube_spike.py <video_id>
"""

import sys
from pathlib import Path

from dotenv import dotenv_values
import youtube_quota
from _shared import get_with_backoff

_env = dotenv_values(Path(__file__).resolve().parent.parent / ".env")

DESCRIPTION_PREVIEW_LENGTH = 300
VIDEOS_LIST_COST_UNITS = 1


def fetch_video(video_id: str) -> dict:
    youtube_quota.charge(VIDEOS_LIST_COST_UNITS, operation="videos.list")
    response = get_with_backoff(
        "https://www.googleapis.com/youtube/v3/videos",
        params={
            "part": "snippet,contentDetails",
            "id": video_id,
            "key": _env["YOUTUBE_API_KEY"],
        },
    )
    return response.json()


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage: youtube_spike.py <video_id>")
        sys.exit(1)

    _script_path, video_id = sys.argv
    items = fetch_video(video_id).get("items", [])
    if not items:
        print("no video found")
        sys.exit(0)

    requested_video = items[0]
    snippet = requested_video["snippet"]
    print(f"title: {snippet.get('title')}")
    print(f"channel: {snippet.get('channelTitle')}")
    print(f"published: {snippet.get('publishedAt')}")
    print(f"tags: {snippet.get('tags')}")
    description_preview = snippet.get("description", "")[:DESCRIPTION_PREVIEW_LENGTH]
    print(f"description (first {DESCRIPTION_PREVIEW_LENGTH} chars): {description_preview}")
