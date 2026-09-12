from pydantic import BaseModel


class VerifiedSongMatch(BaseModel):
    id: int
    artist: str
    title: str
    release_year: int
    gradient_color1: str | None
    gradient_color2: str | None
    confidence: str | None
    cosine_distance: float
