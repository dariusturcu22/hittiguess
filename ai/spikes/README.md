# Metadata source spikes

Throwaway scripts for testing MusicBrainz, Discogs, and Wikidata's live APIs
against real songs, deciding the call/reconcile shape in
[`TASKS.md`'s spike entry](../../docs/TASKS.md) before writing the real
`ai/app/metadata/sources/musicbrainz.py` and `wikidata.py`. Not imported by
the AI microservice, not wired into `service.py`'s pipeline. YouTube's
own spike script exists for comparison only, `youtube.py` is already live.

Run from the `ai/` directory with the project's virtualenv:

```
.venv/Scripts/python.exe spikes/musicbrainz_spike.py "Roygbiv" "Boards of Canada"
.venv/Scripts/python.exe spikes/discogs_spike.py "Roygbiv" "Boards of Canada"
.venv/Scripts/python.exe spikes/wikidata_spike.py "Roygbiv"
.venv/Scripts/python.exe spikes/youtube_spike.py dQw4w9WgXcQ
```

Discogs needs `DISCOGS_CONSUMER_KEY`/`DISCOGS_CONSUMER_SECRET` and YouTube
needs `YOUTUBE_API_KEY` in `ai/.env`, same as the real app. MusicBrainz and
Wikidata need no key.
