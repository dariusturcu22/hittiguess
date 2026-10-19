-- No automatic backfill: there's no reliable mapping from the old PLAYLIST/SPECIAL/ANIME
-- categories to a real genre, so existing rows carry forward with genre left null.
DROP TABLE song_tags;

ALTER TABLE songs ADD COLUMN genre VARCHAR(255);
