from enum import Enum

from pydantic import BaseModel


class LlmExtractionResult(BaseModel):
    """Structured-output shape for both the Wikipedia extraction step and
    the four-source reconciliation step in story 18's lock-or-LLM pipeline.
    Mirrors SongMetadataResult minus the color and source fields, which are
    display and provenance concerns resolved outside the reconciliation
    call."""

    title: str
    artist: str
    release_year: int | None
    confidence: str
    reasoning: str


class SubmissionPreCheckResult(BaseModel):
    """One structured-output call covering everything a submission needs
    before any structured source is queried: splitting the raw YouTube
    video title and channel name into a clean song title, artist, and a
    single flat display color, plus the prompt-injection and song/
    compilation classification checks content_safety.evaluate gates on.
    title/artist are null for a genuinely unidentifiable submission,
    resisting an invented answer rather than guessing."""

    title: str | None
    artist: str | None
    color: str
    contains_injection_attempt: bool
    injection_reasoning: str
    is_song: bool
    is_compilation: bool
    classification_confidence: str
    classification_reasoning: str


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
    color: str
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


class PlaylistVideoIdsRequest(BaseModel):
    playlist_url_or_id: str


class PlaylistVideoIdsResponse(BaseModel):
    video_ids: list[str]
