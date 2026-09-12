-- Story 46: a real owner per playlist and per-member read/write/delete grants,
-- replacing the plain user_playlists many-to-many with playlist_memberships,
-- plus a durable ban record independent of membership.

-- Every playlist and song predating this migration was created before
-- ownership existed as a concept, so there is no real owner to assign one
-- retroactively. They are cleared rather than backfilled with a guessed
-- owner; users are untouched.
DELETE FROM song_artists;
DELETE FROM song_playlists;
DELETE FROM user_playlists;
DELETE FROM songs;
DELETE FROM playlists;

ALTER TABLE playlists ADD COLUMN owner_id BIGINT NOT NULL REFERENCES users (id);

CREATE TABLE playlist_memberships (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    playlist_id BIGINT NOT NULL REFERENCES playlists (id),
    user_id BIGINT NOT NULL REFERENCES users (id),
    can_read BOOLEAN NOT NULL DEFAULT TRUE,
    can_write BOOLEAN NOT NULL DEFAULT TRUE,
    can_delete BOOLEAN NOT NULL DEFAULT TRUE,
    display_name VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(255),
    joined_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (playlist_id, user_id)
);

CREATE TABLE playlist_bans (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    playlist_id BIGINT NOT NULL REFERENCES playlists (id),
    user_id BIGINT NOT NULL REFERENCES users (id),
    banned_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (playlist_id, user_id)
);

DROP TABLE user_playlists;
