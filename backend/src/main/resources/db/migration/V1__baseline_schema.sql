-- Baseline: the schema as Hibernate's ddl-auto=update already produced it,
-- before Flyway took over schema management. Runs for real only against a
-- fresh database with no tables yet (a new local dev environment); an
-- existing database is baselined at this version instead, per
-- spring.flyway.baseline-on-migrate in application.properties.

CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    password VARCHAR(255),
    image_url VARCHAR(255),
    auth_provider VARCHAR(255),
    auth_provider_id VARCHAR(255),
    role VARCHAR(255)
);

CREATE TABLE playlists (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(255),
    color VARCHAR(255),
    invite_code VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE user_playlists (
    user_id BIGINT NOT NULL REFERENCES users (id),
    playlist_id BIGINT NOT NULL REFERENCES playlists (id)
);

CREATE TABLE songs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    artist VARCHAR(255),
    title VARCHAR(255),
    release_year INTEGER NOT NULL,
    youtube_id VARCHAR(255),
    gradient_color1 VARCHAR(255),
    gradient_color2 VARCHAR(255),
    song_tag VARCHAR(255),
    country VARCHAR(255),
    playlist_id BIGINT NOT NULL REFERENCES playlists (id),
    added_by BIGINT NOT NULL REFERENCES users (id)
);

CREATE TABLE refresh_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT UNIQUE REFERENCES users (id),
    expires_at TIMESTAMP NOT NULL
);
