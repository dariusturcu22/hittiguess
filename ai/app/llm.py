from openai import OpenAI

from app.config import settings
from app.schemas import SongMetadataResult

_client = OpenAI(api_key=settings.openai_api_key)


def synthesize(prompt: str) -> SongMetadataResult:
    completion = _client.chat.completions.parse(
        model=settings.openai_model,
        temperature=0.1,
        messages=[{"role": "user", "content": prompt}],
        response_format=SongMetadataResult,
    )

    parsed = completion.choices[0].message.parsed
    if parsed is None:
        raise ValueError("LLM response did not match the expected schema")

    return parsed
