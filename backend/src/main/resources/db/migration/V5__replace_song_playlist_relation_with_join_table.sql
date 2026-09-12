CREATE TABLE song_playlists (
    song_id BIGINT NOT NULL REFERENCES songs (id),
    playlist_id BIGINT NOT NULL REFERENCES playlists (id),
    PRIMARY KEY (song_id, playlist_id)
);

INSERT INTO song_playlists (song_id, playlist_id)
SELECT id, playlist_id
FROM songs;

ALTER TABLE songs DROP COLUMN playlist_id;
