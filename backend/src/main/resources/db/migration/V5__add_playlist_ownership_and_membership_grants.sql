-- Story 46: a real owner per playlist and per-member read/write/delete grants,
-- replacing the plain user_playlists many-to-many with playlist_memberships,
-- plus a durable ban record independent of membership.

ALTER TABLE playlists ADD COLUMN owner_id BIGINT REFERENCES users (id);

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

-- Every existing membership carries full grants forward: nothing before this
-- migration distinguished read, write, and delete, so a member who could
-- already view and edit a playlist keeps doing exactly that. The per-playlist
-- display name and avatar default to the account's own, the same default the
-- join flow now applies going forward.
INSERT INTO playlist_memberships (playlist_id, user_id, can_read, can_write, can_delete, display_name, avatar_url, joined_at)
SELECT user_playlists.playlist_id, user_playlists.user_id, TRUE, TRUE, TRUE, users.username, users.image_url, now()
FROM user_playlists
JOIN users ON users.id = user_playlists.user_id;

-- A playlist can hold songs without ever having had a user_playlists row of
-- its own; a song's contributor is a real user, so that contributor becomes
-- the fallback owner and sole member for a playlist the join table never
-- covered, one membership per playlist from whichever of its songs is oldest.
INSERT INTO playlist_memberships (playlist_id, user_id, can_read, can_write, can_delete, display_name, avatar_url, joined_at)
SELECT DISTINCT ON (songs.playlist_id) songs.playlist_id, songs.added_by, TRUE, TRUE, TRUE, users.username, users.image_url, now()
FROM songs
JOIN users ON users.id = songs.added_by
WHERE songs.playlist_id NOT IN (SELECT playlist_id FROM playlist_memberships)
ORDER BY songs.playlist_id, songs.id ASC;

-- A playlist left with neither a membership nor a song by this point has
-- nothing pointing at it and no user to migrate an owner from; the
-- application never leaves one in that state (the last member leaving
-- deletes the playlist), so this only guards against a genuinely orphaned row.
DELETE FROM playlists
WHERE id NOT IN (SELECT playlist_id FROM playlist_memberships);

-- No creation timestamp exists to identify who actually created a playlist,
-- so the lowest user id among its existing members, its earliest-created
-- account, stands in as the owner for pre-migration data.
UPDATE playlists
SET owner_id = earliest_member.user_id
FROM (
    SELECT DISTINCT ON (playlist_id) playlist_id, user_id
    FROM playlist_memberships
    ORDER BY playlist_id, user_id ASC
) AS earliest_member
WHERE playlists.id = earliest_member.playlist_id;

ALTER TABLE playlists ALTER COLUMN owner_id SET NOT NULL;

DROP TABLE user_playlists;
