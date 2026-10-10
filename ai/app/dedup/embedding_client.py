from app.clients.openai_client import client
from app.config import settings


EMBEDDING_REQUEST_TIMEOUT_SECONDS = 60.0
EMBEDDING_IMMEDIATE_RETRIES = 0


def generate_embedding(text: str) -> list[float]:
    response = client.with_options(timeout=EMBEDDING_REQUEST_TIMEOUT_SECONDS, max_retries=EMBEDDING_IMMEDIATE_RETRIES).embeddings.create(
        model=settings.embedding_model, input=text
    )
    (single_embedding_result,) = response.data
    return single_embedding_result.embedding
