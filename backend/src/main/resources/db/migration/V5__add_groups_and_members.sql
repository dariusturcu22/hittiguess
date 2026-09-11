CREATE TABLE groups (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invite_code VARCHAR(255) NOT NULL UNIQUE,
    join_code VARCHAR(4) NOT NULL UNIQUE,
    status VARCHAR(255) NOT NULL,
    dj_mode VARCHAR(255) NOT NULL,
    win_condition_card_count INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP
);

CREATE TABLE group_playlists (
    group_id BIGINT NOT NULL REFERENCES groups (id),
    playlist_id BIGINT NOT NULL REFERENCES playlists (id)
);

-- A member's user_id is unique across the whole table, not just within one group:
-- a user can belong to at most one group at a time, and this enforces it at the
-- schema level rather than relying only on the application check.
CREATE TABLE members (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES groups (id),
    user_id BIGINT NOT NULL UNIQUE REFERENCES users (id),
    display_name VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(255),
    is_admin BOOLEAN NOT NULL,
    is_connected BOOLEAN NOT NULL,
    is_in_voice BOOLEAN NOT NULL,
    joined_at TIMESTAMP NOT NULL
);
