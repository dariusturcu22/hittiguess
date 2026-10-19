-- Carries the raw YouTube video title and uploading channel name per import job item,
-- captured at job creation time, so a still-processing row has something real to show
-- before the metadata pipeline resolves it into a catalog song.
ALTER TABLE playlist_import_job_items ADD COLUMN raw_title VARCHAR(500);
ALTER TABLE playlist_import_job_items ADD COLUMN raw_channel_title VARCHAR(255);
