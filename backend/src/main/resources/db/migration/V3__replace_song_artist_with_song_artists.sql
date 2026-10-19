CREATE TABLE song_artists (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    song_id BIGINT NOT NULL REFERENCES songs (id),
    name VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    display_order INTEGER NOT NULL
);

INSERT INTO song_artists (song_id, name, role, display_order)
SELECT id, artist, 'MAIN', 0
FROM songs
WHERE artist IS NOT NULL;

ALTER TABLE songs DROP COLUMN artist;
