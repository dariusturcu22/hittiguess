"""Bodies of the per-stage pipeline endpoints. Field names are camelCase on the wire,
except the final result, which keeps the snake_case shape of /metadata/resolve's
response so core reads both with one type."""

from enum import StrEnum
from typing import Any

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

from app.metadata.schemas import MetadataResolveResponse, RejectionReason


class StageModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class YoutubeMetadataStageRequest(StageModel):
    youtube_id: str


class VideoFacts(StageModel):
    video_title: str
    channel_title: str
    duration_seconds: int | None = None
    category_id: str
    upload_year: int | None = None


class YoutubeStageOutcome(StrEnum):
    IDENTIFIED = "IDENTIFIED"
    REJECTED = "REJECTED"
    DUPLICATE = "DUPLICATE"


class YoutubeMetadataStageResponse(StageModel):
    """IDENTIFIED carries the precheck's title, main artists, featured artists, and
    color for the source stages. REJECTED (the hard filter or the precheck's safety
    gate) and DUPLICATE (an already verified song) finish the run: result is then the
    final response."""

    outcome: YoutubeStageOutcome
    video: VideoFacts
    title: str | None = None
    artists: list[str] = []
    featured_artists: list[str] = []
    color: str | None = None
    rejection_reason: RejectionReason | None = None
    rejection_detail: str | None = None
    result: MetadataResolveResponse | None = None


class SourceStageRequest(StageModel):
    """title and artists are the YouTube stage's title and main artists, unchanged."""

    title: str
    artists: list[str]
    youtube_id: str | None = None


class StructuredSourceStageResponse(StageModel):
    candidates: list[dict[str, Any]]
    earliest_year: int | None


class WikidataStageResponse(StructuredSourceStageResponse):
    sitelinks_count: int | None = None


class WikipediaStageResponse(StageModel):
    candidates: list[dict[str, Any]]
    earliest_year: int | None = None


class ReconcileStageRequest(StageModel):
    title: str
    artists: list[str]
    featured_artists: list[str] = []
    color: str
    video: VideoFacts
    musicbrainz_candidates: list[dict[str, Any]] = []
    discogs_candidates: list[dict[str, Any]] = []
    wikidata_candidates: list[dict[str, Any]] = []
    wikipedia_candidates: list[dict[str, Any]] = []
    sitelinks_count: int | None = None
    youtube_id: str | None = None


class StageFailureCause(StrEnum):
    HTTP_ERROR = "HTTP_ERROR"
    TIMEOUT = "TIMEOUT"
    CONNECTION_ERROR = "CONNECTION_ERROR"
    NO_USABLE_METADATA = "NO_USABLE_METADATA"
    OTHER = "OTHER"


class StageProvider(StrEnum):
    YOUTUBE = "YOUTUBE"
    MUSICBRAINZ = "MUSICBRAINZ"
    DISCOGS = "DISCOGS"
    WIKIDATA = "WIKIDATA"
    WIKIPEDIA = "WIKIPEDIA"
    DEEPINFRA = "DEEPINFRA"
    OPENAI = "OPENAI"


class ProviderHint(StrEnum):
    """The provider kinds core's FailureClassifier distinguishes."""

    YOUTUBE_VIDEO_LOOKUP = "YOUTUBE_VIDEO_LOOKUP"
    API_KEY_PROVIDER = "API_KEY_PROVIDER"
    KEYLESS_PROVIDER = "KEYLESS_PROVIDER"


class StageErrorBody(StageModel):
    """upstreamStatus is the HTTP status the provider answered with, null when the
    call got no response. provider is null when the failure is not an upstream one."""

    stage: str
    provider: StageProvider | None
    provider_hint: ProviderHint
    cause: StageFailureCause
    upstream_status: int | None
    message: str


class StagePaceLimitBody(StageModel):
    stage: str
    retry_after_seconds: int
    message: str
