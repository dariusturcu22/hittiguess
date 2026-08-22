# ARCHIVE.md: Completed Stories

Stories move here from [PROJECT_STATE.md](PROJECT_STATE.md) once their status reaches Implemented and every task under them in [TASKS.md](TASKS.md) is checked off. Kept for history, not read during normal sessions.

## Story 6: Two-service split

Area: Infra. Split the backend into a Spring Boot core service and a Python/FastAPI AI microservice (`ai/`). The AI microservice owns the full metadata pipeline: the source integrations (YouTube live; MusicBrainz, Wikipedia, and Genius all paused, see below), prompt construction, and LLM synthesis through OpenAI's structured output mode with Pydantic validation, replacing the previous regex-stripped manual JSON parse. It exposes a single internal endpoint, `POST /metadata/resolve`, gated by a shared secret header (`X-Internal-Api-Key`). The core service's `SongMetadataService` calls that endpoint over a `RestClient`, keeping `SongMetadataController`'s public contract, `GET /api/metadata/song` and the `AiResponse` shape, unchanged. The one-in-flight-request-per-user rate limit stays in the core service. Nine files whose logic moved to the AI microservice were deleted from the core service, along with the Spring AI dependency.

AI microservice:
- [x] Scaffold the FastAPI project structure, with pytest configured
- [x] Port the four metadata source integrations (YouTube Data API, MusicBrainz, Wikipedia, Genius) to Python. Only YouTube makes live calls right now. Genius called an undocumented endpoint with a browser-spoofed User-Agent, which violates CLAUDE.md's official-APIs-only rule, its official API needs a registered client and bearer token the project doesn't have yet. MusicBrainz and Wikipedia call real, documented APIs with no rule violation, but were paused alongside Genius pending one deliberate review of API usage, cost, and User-Agent contact info across all three, rather than reviewing them piecemeal. All three `search()` functions return no result until that review happens; see the 2026-08 "Pause MusicBrainz, Wikipedia, and Genius" entry in `docs/DECISIONS.md`
- [x] Port `MetadataPromptBuilder`'s prompt-construction logic
- [x] Replace the regex-stripped LLM response parsing with OpenAI's structured output / JSON schema mode and Pydantic model validation
- [x] Add an internal endpoint, `POST /metadata/resolve`, that gathers the available sources and returns the synthesized result
- [x] Add basic tests: prompt building, URL building, response parsing/validation
- [x] Own `OPENAI_API_KEY` and `YOUTUBE_API_KEY` in its own config

Core service:
- [x] Rewrite `SongMetadataService` to call the AI microservice's internal endpoint over HTTP, keeping `SongMetadataController`'s public contract unchanged
- [x] Keep the one-in-flight-request-per-user rate limit in the core service
- [x] Authenticate between the two services with a shared secret header
- [x] Remove the Spring AI dependency
- [x] Delete the files whose logic moved entirely to the AI microservice: `YouTubeMetadataService`, `MusicBrainzService`, `WikipediaService`, `GeniusService`, `MetadataParser`, `MetadataPromptBuilder`, `UrlBuilder`, `HttpUtils`, `FlexibleYearDeserializer`
- [x] Drop `OPENAI_API_KEY` and `YOUTUBE_API_KEY` from the core service's env

Frontend fix (found while confirming these tasks):
- [x] `AddSongForm.tsx`'s `handleGetDetails` now treats a 200 response with `status: "ERROR"` the same as a thrown error
