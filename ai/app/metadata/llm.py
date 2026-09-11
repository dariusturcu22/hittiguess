from typing import TypeVar

from openai import APIStatusError
from pydantic import BaseModel

from app.clients.deepinfra_client import client as deepinfra_client
from app.clients.openai_client import client
from app.config import settings
from app.metadata.schemas import SongMetadataResult

# Non-zero temperature produces real run-to-run answer variance on close
# extraction calls, not just wording differences in a reasoning field.
STRUCTURED_OUTPUT_TEMPERATURE = 0.0
RESPONSE_FORMAT_NOT_SUPPORTED_MESSAGE = "response format is not supported"
EXTRACTION_TOOL_NAME = "extract_structured_result"

ResponseModel = TypeVar("ResponseModel", bound=BaseModel)


def synthesize(prompt: str) -> SongMetadataResult:
    completion = client.chat.completions.parse(
        model=settings.openai_model,
        temperature=0.1,
        messages=[{"role": "user", "content": prompt}],
        response_format=SongMetadataResult,
    )

    parsed = completion.choices[0].message.parsed
    if parsed is None:
        raise ValueError("LLM response did not match the expected schema")

    return parsed


def _extract_via_tool_call(prompt: str, response_model: type[ResponseModel]) -> ResponseModel:
    """Fallback for a model that rejects response_format's json_schema mode
    outright (observed live on a different DeepInfra-hosted model,
    Llama-3.1-8B-Instruct-Turbo, during story 20's spike) but still honors
    forced tool-calling, a separate structured-output mechanism most
    OpenAI-compatible APIs also support."""
    tool = {
        "type": "function",
        "function": {
            "name": EXTRACTION_TOOL_NAME,
            "description": "Record the extracted structured result.",
            "parameters": response_model.model_json_schema(),
        },
    }
    completion = deepinfra_client.chat.completions.create(
        model=settings.deepinfra_model,
        temperature=STRUCTURED_OUTPUT_TEMPERATURE,
        messages=[{"role": "user", "content": prompt}],
        tools=[tool],
        tool_choice={"type": "function", "function": {"name": EXTRACTION_TOOL_NAME}},
    )
    tool_calls = completion.choices[0].message.tool_calls
    if not tool_calls:
        raise ValueError(f"{settings.deepinfra_model} did not call {EXTRACTION_TOOL_NAME} via forced tool use")
    return response_model.model_validate_json(tool_calls[0].function.arguments)


def extract_structured(prompt: str, response_model: type[ResponseModel]) -> ResponseModel:
    """Runs a structured-output extraction call against DeepSeek-V4-Flash on
    DeepInfra, the model story 18's Wikipedia-reading step depends on.
    Generic over response_model since each caller defines its own extraction
    shape; falls back to forced tool-calling when the model rejects
    response_format's json_schema mode outright."""
    try:
        completion = deepinfra_client.chat.completions.parse(
            model=settings.deepinfra_model,
            temperature=STRUCTURED_OUTPUT_TEMPERATURE,
            messages=[{"role": "user", "content": prompt}],
            response_format=response_model,
        )
    except APIStatusError as api_status_error:
        response_body = api_status_error.body
        error_message = str(response_body.get("message", "")) if response_body else ""
        if RESPONSE_FORMAT_NOT_SUPPORTED_MESSAGE in error_message:
            return _extract_via_tool_call(prompt, response_model)
        raise

    parsed = completion.choices[0].message.parsed
    if parsed is None:
        raise ValueError(f"{settings.deepinfra_model} response did not match the expected schema")
    return parsed
