from pydantic import BaseModel


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


class MetadataResolveResponse(BaseModel):
    status: str
    model: str
    content: SongMetadataResult | None = None
