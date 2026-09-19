from pydantic import BaseModel


class LlmExtractionResult(BaseModel):
    """Shared structured-output shape every story 20 candidate is tested
    against, so results are comparable across providers. Mirrors the
    production SongMetadataResult (app/metadata/schemas.py) minus the
    gradient-color fields, which are a display concern, not an extraction
    accuracy one."""

    title: str
    artist: str
    release_year: int | None
    confidence: str
    reasoning: str
