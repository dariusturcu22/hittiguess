CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE songs
    ADD COLUMN embedding vector(1536);

CREATE INDEX songs_embedding_hnsw_idx ON songs USING hnsw (embedding vector_cosine_ops);
