from pydantic import BaseModel


class VerifiedSongMatch(BaseModel):
    id: int
    main_artists: list[str] = []
    featured_artists: list[str] = []
    title: str
    release_year: int
    color: str | None
    confidence: str | None
    cosine_distance: float
