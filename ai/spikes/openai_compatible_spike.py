"""Spike: exercise any OpenAI-compatible chat completions endpoint against
the shared LlmExtractionResult schema, covering the story 20 candidates that
expose an OpenAI-compatible API (Groq, DeepInfra, llama.cpp's local server)
plus the OpenAI benchmark models (gpt-5.1, gpt-5-mini, gpt-5-nano)
themselves, so all of them go through the same code path for a fair
comparison. Zhipu's provider config stays here unused, see spikes/README.md
for why it's dropped from the shortlist.

Tries response_format's json_schema mode first; if a provider rejects that
mode outright (observed on DeepInfra's Llama-3.1-8B-Instruct-Turbo), falls
back to forced tool-calling automatically, a separate structured-output
mechanism most OpenAI-compatible APIs also support.

Usage: python spikes/openai_compatible_spike.py <provider> <model> "<title>" "<artist>"
Example: python spikes/openai_compatible_spike.py groq openai/gpt-oss-20b "Roygbiv" "Boards of Canada"
"""

import sys
from pathlib import Path

from dotenv import dotenv_values
from openai import APIStatusError, BadRequestError, OpenAI

from llm_schemas import LlmExtractionResult

_env = dotenv_values(Path(__file__).resolve().parent.parent / ".env")

STRUCTURED_OUTPUT_TEMPERATURE = 0.0
# Non-zero temperature produces real run-to-run answer variance on close
# reconciliation calls, not just wording differences in the reasoning
# field. 0 is the closest available lever to deterministic, though some
# providers still aren't perfectly repeatable even at 0 due to internal
# request batching.
# A hung provider-side request otherwise waits forever, the SDK sets no
# default timeout of its own.
REQUEST_TIMEOUT_SECONDS = 90.0

# base_url=None means the provider's own default (OpenAI itself).
PROVIDERS: dict[str, dict[str, str | None]] = {
    "openai": {"base_url": None, "api_key_env": "OPENAI_API_KEY"},
    "zhipu": {"base_url": "https://api.z.ai/api/paas/v4/", "api_key_env": "ZHIPU_API_KEY"},
    "groq": {"base_url": "https://api.groq.com/openai/v1", "api_key_env": "GROQ_API_KEY"},
    "deepinfra": {"base_url": "https://api.deepinfra.com/v1/openai", "api_key_env": "DEEPINFRA_API_KEY"},
    # llama-server (ai/local-llm/) takes no API key; any non-empty string satisfies the SDK.
    "llamacpp": {"base_url": "http://127.0.0.1:8090/v1", "api_key_env": None},
}
LLAMACPP_PLACEHOLDER_API_KEY = "not-needed"


def build_client(provider: str) -> OpenAI:
    config = PROVIDERS[provider]
    if config["api_key_env"] is None:
        api_key = LLAMACPP_PLACEHOLDER_API_KEY
    else:
        api_key = _env.get(config["api_key_env"])
        if not api_key:
            raise ValueError(f"{config['api_key_env']} is not set in ai/.env")
    return OpenAI(api_key=api_key, base_url=config["base_url"], timeout=REQUEST_TIMEOUT_SECONDS)


EXTRACTION_TOOL_NAME = "extract_song_metadata"


def _extract_via_tool_call(client: OpenAI, provider: str, model: str, prompt: str) -> LlmExtractionResult:
    """Fallback for models that reject response_format's json_schema mode
    outright (observed on DeepInfra's Llama-3.1-8B-Instruct-Turbo) but still
    honor forced tool-calling, a separate structured-output mechanism."""
    tool = {
        "type": "function",
        "function": {
            "name": EXTRACTION_TOOL_NAME,
            "description": "Record the extracted song metadata.",
            "parameters": LlmExtractionResult.model_json_schema(),
        },
    }
    completion = client.chat.completions.create(
        model=model,
        temperature=STRUCTURED_OUTPUT_TEMPERATURE,
        messages=[{"role": "user", "content": prompt}],
        tools=[tool],
        tool_choice={"type": "function", "function": {"name": EXTRACTION_TOOL_NAME}},
    )
    tool_calls = completion.choices[0].message.tool_calls
    if not tool_calls:
        raise ValueError(f"{provider}/{model} did not call {EXTRACTION_TOOL_NAME} via forced tool use")
    return LlmExtractionResult.model_validate_json(tool_calls[0].function.arguments)


def extract(provider: str, model: str, prompt: str) -> LlmExtractionResult:
    client = build_client(provider)
    request_kwargs = {
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        "response_format": LlmExtractionResult,
    }
    try:
        completion = client.chat.completions.parse(temperature=STRUCTURED_OUTPUT_TEMPERATURE, **request_kwargs)
    except BadRequestError as bad_request_error:
        # Some models (observed on gpt-5-nano) only accept the default temperature
        # and reject any explicit value, including the one matching their default.
        if bad_request_error.body and bad_request_error.body.get("param") == "temperature":
            completion = client.chat.completions.parse(**request_kwargs)
        else:
            raise
    except APIStatusError as api_status_error:
        # Some models (observed on DeepInfra's Llama-3.1-8B-Instruct-Turbo) don't
        # support response_format's json_schema mode at all; forced tool-calling
        # is a separate mechanism worth trying before giving up on the model.
        if api_status_error.body and "response format is not supported" in str(api_status_error.body.get("message", "")):
            return _extract_via_tool_call(client, provider, model, prompt)
        raise

    parsed = completion.choices[0].message.parsed
    if parsed is None:
        raw_content = completion.choices[0].message.content
        raise ValueError(
            f"{provider}/{model} response did not match the expected schema. Raw content: {raw_content!r}"
        )
    return parsed


def build_smoke_test_prompt(title: str, artist: str) -> str:
    return (
        "You are a music metadata analyst. Given only this song title and artist, "
        "with no external source data, extract the cleaned title, artist, your best-guess "
        "original release year (null if you genuinely don't know), a confidence level "
        "(high/medium/low), and your reasoning. The response fields are enforced by a JSON "
        f"schema, do not describe the JSON shape yourself.\n\nTitle: {title}\nArtist: {artist}"
    )


if __name__ == "__main__":
    if len(sys.argv) != 5:
        print('usage: openai_compatible_spike.py <provider> <model> "<title>" "<artist>"')
        print(f"providers: {', '.join(PROVIDERS)}")
        sys.exit(1)

    _script_path, provider_argument, model_argument, title_argument, artist_argument = sys.argv
    result = extract(provider_argument, model_argument, build_smoke_test_prompt(title_argument, artist_argument))
    print(result.model_dump_json(indent=2))
