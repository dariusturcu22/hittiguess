"""Spike: exercise AWS Bedrock's Nova Micro against the shared
LlmExtractionResult schema. Bedrock's Converse API has no OpenAI-style
response_format, structured output here goes through forced tool use
instead: declare one tool shaped like the schema and require the model to
call it. See spikes/README.md.

Usage: python spikes/bedrock_spike.py "<title>" "<artist>"
"""

import sys
from pathlib import Path

import boto3
from dotenv import dotenv_values

from llm_schemas import LlmExtractionResult

_env = dotenv_values(Path(__file__).resolve().parent.parent / ".env")

MODEL_ID = "us.amazon.nova-micro-v1:0"
EXTRACTION_TOOL_NAME = "extract_song_metadata"

EXTRACTION_TOOL = {
    "toolSpec": {
        "name": EXTRACTION_TOOL_NAME,
        "description": "Record the extracted song metadata.",
        "inputSchema": {
            "json": {
                "type": "object",
                "properties": {
                    "title": {"type": "string"},
                    "artist": {"type": "string"},
                    "release_year": {"type": ["integer", "null"]},
                    "confidence": {"type": "string", "enum": ["high", "medium", "low"]},
                    "reasoning": {"type": "string"},
                },
                "required": ["title", "artist", "release_year", "confidence", "reasoning"],
            }
        },
    }
}


def build_client():
    return boto3.client(
        "bedrock-runtime",
        region_name=_env.get("AWS_REGION"),
        aws_access_key_id=_env.get("AWS_ACCESS_KEY_ID"),
        aws_secret_access_key=_env.get("AWS_SECRET_ACCESS_KEY"),
    )


def extract(prompt: str) -> LlmExtractionResult:
    client = build_client()
    response = client.converse(
        modelId=MODEL_ID,
        messages=[{"role": "user", "content": [{"text": prompt}]}],
        toolConfig={
            "tools": [EXTRACTION_TOOL],
            "toolChoice": {"tool": {"name": EXTRACTION_TOOL_NAME}},
        },
    )

    content_blocks = response["output"]["message"]["content"]
    tool_use_block = next((block["toolUse"] for block in content_blocks if "toolUse" in block), None)
    if tool_use_block is None:
        raise ValueError(f"Nova Micro did not call {EXTRACTION_TOOL_NAME}. Raw content: {content_blocks!r}")

    return LlmExtractionResult.model_validate(tool_use_block["input"])


def build_smoke_test_prompt(title: str, artist: str) -> str:
    return (
        "You are a music metadata analyst. Given only this song title and artist, "
        "with no external source data, extract the cleaned title, artist, your best-guess "
        "original release year (null if you genuinely don't know), a confidence level "
        f"(high/medium/low), and your reasoning.\n\nTitle: {title}\nArtist: {artist}"
    )


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print('usage: bedrock_spike.py "<title>" "<artist>"')
        sys.exit(1)

    _script_path, title_argument, artist_argument = sys.argv
    result = extract(build_smoke_test_prompt(title_argument, artist_argument))
    print(result.model_dump_json(indent=2))
