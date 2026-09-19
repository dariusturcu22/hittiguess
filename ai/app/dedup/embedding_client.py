from app.clients.openai_client import client
from app.config import settings


def generate_embedding(text: str) -> list[float]:
    response = client.embeddings.create(model=settings.embedding_model, input=text)
    single_embedding_result = response.data[0]
    return single_embedding_result.embedding
