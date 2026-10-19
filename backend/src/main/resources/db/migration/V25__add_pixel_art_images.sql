-- Pixel-art image rule: covers and avatars upload pixelized and live as PNG bytes
-- in the database, never as hotlinked originals. Nullable: playlists without a
-- custom cover keep the song-thumbnail mosaic, users without an avatar keep
-- their initial.
ALTER TABLE playlists ADD COLUMN cover_image BYTEA;
ALTER TABLE users ADD COLUMN avatar_image BYTEA;
