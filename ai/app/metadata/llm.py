from app.clients.openai_client import client
from app.config import settings
from app.metadata.schemas import SongMetadataResult


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
