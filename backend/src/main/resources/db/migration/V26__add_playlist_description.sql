-- Free-text playlist description, editable from the Edit playlist page. Nullable:
-- a playlist without one keeps rendering with no description shown.
ALTER TABLE playlists ADD COLUMN description VARCHAR(300);
