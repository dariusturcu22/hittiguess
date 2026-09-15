from enum import Enum

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


class RejectionReason(str, Enum):
    NOT_MUSIC = "NOT_MUSIC"
    COMPILATION = "COMPILATION"
    PROMPT_INJECTION = "PROMPT_INJECTION"


class SongClassification(BaseModel):
    is_song: bool
    is_compilation: bool
    confidence: str
    reasoning: str


class InjectionCheckResult(BaseModel):
    contains_injection_attempt: bool
    reasoning: str


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
    rejection_reason: RejectionReason | None = None
    rejection_detail: str | None = None
