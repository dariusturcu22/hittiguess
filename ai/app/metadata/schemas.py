from pydantic import BaseModel


class LlmExtractionResult(BaseModel):
    """Structured-output shape for both the Wikipedia extraction step and
    the four-source reconciliation step in story 18's lock-or-LLM pipeline.
    Mirrors SongMetadataResult minus the gradient-color and source fields,
    which are display and provenance concerns resolved after verification."""

    title: str
    artist: str
    release_year: int | None
    confidence: str
    reasoning: str


class MetadataResolveRequest(BaseModel):
    youtube_url: str


class SongMetadataResult(BaseModel):
    title: str
    artist: str
    release_year: int | None
    gradient_color1: str
    gradient_color2: str
    confidence: str
    source: str
    reasoning: str
    verification_status: str | None = None


class MetadataResolveResponse(BaseModel):
    status: str
    model: str
    content: SongMetadataResult | None = None
