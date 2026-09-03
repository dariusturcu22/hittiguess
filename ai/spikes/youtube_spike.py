"""Spike: YouTube Data API, kept for side-by-side comparison against the
structured sources, the real source lives at app/metadata/sources/youtube.py
already. See spikes/README.md.

Usage: python spikes/youtube_spike.py <video_id>
"""

import sys
from pathlib import Path

from dotenv import dotenv_values
from _shared import get_with_backoff

_env = dotenv_values(Path(__file__).resolve().parent.parent / ".env")

DESCRIPTION_PREVIEW_LENGTH = 300


def fetch_video(video_id: str) -> dict:
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

    items = fetch_video(sys.argv[1]).get("items", [])
    if not items:
        print("no video found")
        sys.exit(0)

    snippet = items[0]["snippet"]
    print(f"title: {snippet.get('title')}")
    print(f"channel: {snippet.get('channelTitle')}")
    print(f"published: {snippet.get('publishedAt')}")
    print(f"tags: {snippet.get('tags')}")
    description_preview = snippet.get("description", "")[:DESCRIPTION_PREVIEW_LENGTH]
    print(f"description (first {DESCRIPTION_PREVIEW_LENGTH} chars): {description_preview}")
