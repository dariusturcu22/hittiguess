-- A ban row belongs to both its playlist and the banned user, so deleting either one
-- removes it. Without this, deleting the account of a previously banned user failed on
-- the playlist_bans foreign key.

ALTER TABLE playlist_bans
    DROP CONSTRAINT IF EXISTS playlist_bans_user_id_fkey,
    ADD CONSTRAINT playlist_bans_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE playlist_bans
    DROP CONSTRAINT IF EXISTS playlist_bans_playlist_id_fkey,
    ADD CONSTRAINT playlist_bans_playlist_id_fkey FOREIGN KEY (playlist_id) REFERENCES playlists (id) ON DELETE CASCADE;
