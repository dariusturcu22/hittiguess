-- Public playlists (story 30's narrow public-browse slice). A playlist can be published
-- publicly independent of ownership or membership; publishing changes nothing about grants
-- or who is a member. Defaults to false so no existing playlist becomes visible on upgrade.
ALTER TABLE playlists ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT FALSE;

-- Saving a public playlist into a user's own library is a bookmark, not membership: no
-- grants, no per-playlist identity, no interaction with playlist_memberships or
-- playlist_bans. The unique constraint stops the same user saving the same playlist twice.
CREATE TABLE saved_playlists (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    playlist_id BIGINT NOT NULL REFERENCES playlists (id),
    saved_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_saved_playlist_user_playlist UNIQUE (user_id, playlist_id)
);

-- A user's saved-playlists list reads every row for that user in one query.
CREATE INDEX idx_saved_playlists_user_id ON saved_playlists (user_id);
