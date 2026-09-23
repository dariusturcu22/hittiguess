from enum import Enum
from typing import Literal

from pydantic import BaseModel, field_validator

ConfidenceLevel = Literal["low", "medium", "high"]
LOW_CONFIDENCE = "low"
VALID_CONFIDENCE_LEVELS = frozenset({"low", "medium", "high"})


def _normalize_confidence(value: object) -> ConfidenceLevel:
    normalized_value = str(value).strip().lower()
    if normalized_value in VALID_CONFIDENCE_LEVELS:
        return normalized_value
    return LOW_CONFIDENCE


class ConfidenceNormalizedModel(BaseModel):
    @field_validator("confidence", mode="before", check_fields=False)
    @classmethod
    def normalize_confidence(cls, value: object) -> ConfidenceLevel:
        return _normalize_confidence(value)


class LlmExtractionResult(ConfidenceNormalizedModel):
    """Structured-output shape for both the Wikipedia extraction step and
    the four-source reconciliation step in story 18's lock-or-LLM pipeline.
    Mirrors SongMetadataResult minus the color and source fields, which are
    display and provenance concerns resolved outside the reconciliation
    call."""

    title: str
    artist: str
    release_year: int | None
    confidence: ConfidenceLevel
    reasoning: str


class SubmissionPreCheckResult(BaseModel):
    """One structured-output call covering everything a submission needs
    before any structured source is queried: splitting the raw YouTube
    video title and channel name into a clean song title, main artists,
    featured artists, and a single flat display color, plus the
    prompt-injection and song/compilation classification checks
    content_safety.evaluate gates on. title and main artists are empty for
    a genuinely unidentifiable submission, resisting an invented answer
    rather than guessing."""

    title: str | None
    main_artists: list[str] = []
    featured_artists: list[str] = []
    color: str
    contains_injection_attempt: bool
    injection_reasoning: str
    is_song: bool
    is_compilation: bool
    classification_confidence: ConfidenceLevel
    classification_reasoning: str

    @field_validator("classification_confidence", mode="before")
    @classmethod
    def normalize_classification_confidence(cls, value: object) -> ConfidenceLevel:
        return _normalize_confidence(value)


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


class SongMetadataResult(ConfidenceNormalizedModel):
    title: str
    main_artists: list[str] = []
    featured_artists: list[str] = []
    release_year: int | None
    color: str
    confidence: ConfidenceLevel
    source: str
    reasoning: str
    verification_status: str | None = None
    sitelinks_count: int | None = None


class MetadataResolveResponse(BaseModel):
    status: str
    model: str
    content: SongMetadataResult | None = None
    rejection_reason: RejectionReason | None = None
    rejection_detail: str | None = None


class PlaylistVideoIdsRequest(BaseModel):
    playlist_url_or_id: str


class PlaylistVideoIdsResponse(BaseModel):
    video_ids: list[str]
