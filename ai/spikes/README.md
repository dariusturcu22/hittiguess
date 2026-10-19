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
.venv/Scripts/python.exe spikes/wikidata_spike.py "Roygbiv" "Boards of Canada"
.venv/Scripts/python.exe spikes/youtube_spike.py dQw4w9WgXcQ
```

Discogs needs `DISCOGS_CONSUMER_KEY`/`DISCOGS_CONSUMER_SECRET` and YouTube
needs `YOUTUBE_API_KEY` in `ai/.env`, same as the real app. MusicBrainz and
Wikidata need no key.

`run_matrix.py`'s `SONGS` list is deliberately adversarial past a small
sanity baseline: reissue/pressing history, cover-version attribution risk,
thin source coverage, title collisions, and multi-artist/remix credit
strings, each a real song with a documented reason to expect friction.
`extraction_test_set.py` is a separate list of messy raw submission
strings (typos, upload-artifact noise, a deliberately unanswerable case)
for testing an LLM's title/artist cleaning judgment directly, independent
of source-API reconciliation, no source lookups involved.

## Story 20: local/cheap LLM candidates

Same throwaway-script treatment, testing each shortlisted candidate against
the shared `LlmExtractionResult` schema (`llm_schemas.py`) so results are
comparable. `openai_compatible_spike.py` covers every candidate that exposes
an OpenAI-compatible endpoint (OpenAI itself, Groq, DeepInfra; the `zhipu`
provider config is still there but unused, see below):

```
.venv/Scripts/python.exe spikes/openai_compatible_spike.py openai gpt-5-nano "Roygbiv" "Boards of Canada"
.venv/Scripts/python.exe spikes/openai_compatible_spike.py groq openai/gpt-oss-20b "Roygbiv" "Boards of Canada"
.venv/Scripts/python.exe spikes/openai_compatible_spike.py deepinfra meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo "Roygbiv" "Boards of Canada"
```

AWS Bedrock's Converse API has no OpenAI-style `response_format`, so Nova
Micro gets its own script using forced tool use instead:

```
.venv/Scripts/python.exe spikes/bedrock_spike.py "Roygbiv" "Boards of Canada"
```

llama.cpp runs as `llama-server.exe` (`ai/local-llm/bin/`, gitignored, the
Vulkan-backend Windows build) serving an OpenAI-compatible endpoint on
`localhost:8090`, so it reuses `openai_compatible_spike.py` with the
`llamacpp` provider, no API key needed:

```
ai/local-llm/bin/llama-server.exe -m ../models/Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf --port 8090 -ngl 99 --jinja -c 4096
.venv/Scripts/python.exe spikes/openai_compatible_spike.py llamacpp Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf "Roygbiv" "Boards of Canada"
```

**Confirmed running on CPU, not the laptop's Arc iGPU**, despite `-ngl 99`.
`~10 tokens/sec` generation, no Vulkan device ever appears in the server
log. Checked directly: the Vulkan loader (`vulkan-1.dll`) is present, but
`HKLM\SOFTWARE\Khronos\Vulkan\Drivers` (where a working GPU driver
registers its Vulkan ICD) has no entries at all, on either the 64-bit or
WOW6432Node registry path, despite a November 2025 Arc Graphics driver
being installed. The GPU driver itself isn't exposing Vulkan to the system;
needs a driver update or repair from Intel directly to get GPU offload
working. Structured output itself is confirmed correct on CPU already, this
only affects throughput, not whether the candidate clears the bar.

Needs `GROQ_API_KEY`, `DEEPINFRA_API_KEY`, and
`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`/`AWS_REGION` in `ai/.env`.

**Zhipu dropped entirely, on two independent grounds, both checked directly
rather than assumed.** Free tier: GLM-4.5-Flash and GLM-4.7-Flash both
tested, neither clears the structured-output bar despite Z.ai's own docs
claiming JSON-schema support. `response_format: {"type": "json_schema",
...}` is silently ignored on both models, the model free-writes prose with
a markdown-fenced JSON block instead (wrong field names included).
GLM-4.5-Flash's forced tool-calling produces corrupted output (a malformed
float literal, a leaked `</tool_call>` tag inside the arguments string);
GLM-4.7-Flash's forced tool-calling instead hung indefinitely with no
response at all, two different failure modes on the same mechanism. Both
are the "best-effort JSON" failure mode `AGENTS.md`'s rule against
regex-parsing exists to catch. Paid tier: GLM-5.3-Flash, the newest and
cheapest paid option, isn't price-competitive regardless, its list price
($0.15/$0.50 per million) and promo price ($0.075/$0.25 through
2026-09-09) both cost more on input than `gpt-5-nano`'s $0.05, which is
already confirmed working. `ZHIPU_API_KEY` stays in `ai/.env` and the
`zhipu` provider config stays in `openai_compatible_spike.py` in case a
future model is worth a fresh look, but nothing currently in the catalog
is both cheaper and functional.
