CREATE TABLE song_tags (
    song_id BIGINT NOT NULL REFERENCES songs (id),
    tag VARCHAR(255) NOT NULL
);

-- NONE meant "no tags selected" under the old single-enum field; that's now
-- an empty collection, not a row carrying the literal value NONE.
INSERT INTO song_tags (song_id, tag)
SELECT id, song_tag
FROM songs
WHERE song_tag IS NOT NULL AND song_tag <> 'NONE';

ALTER TABLE songs DROP COLUMN song_tag;
