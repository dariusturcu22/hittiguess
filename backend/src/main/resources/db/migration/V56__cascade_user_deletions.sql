-- Story Release 1: Account deletion completeness.
-- Ensure user foreign keys cascade on delete or set null where appropriate.

ALTER TABLE two_factor_backup_codes
    DROP CONSTRAINT IF EXISTS two_factor_backup_codes_user_id_fkey,
    ADD CONSTRAINT two_factor_backup_codes_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE chat_messages
    DROP CONSTRAINT IF EXISTS chat_messages_sender_id_fkey,
    ADD CONSTRAINT chat_messages_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE saved_playlists
    DROP CONSTRAINT IF EXISTS saved_playlists_user_id_fkey,
    ADD CONSTRAINT saved_playlists_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE members
    DROP CONSTRAINT IF EXISTS members_user_id_fkey,
    ADD CONSTRAINT members_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE players
    DROP CONSTRAINT IF EXISTS players_user_id_fkey,
    ADD CONSTRAINT players_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE song_confirmations
    DROP CONSTRAINT IF EXISTS song_confirmations_user_id_fkey,
    ADD CONSTRAINT song_confirmations_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE song_reports
    ALTER COLUMN reporter_id DROP NOT NULL,
    DROP CONSTRAINT IF EXISTS song_reports_reporter_id_fkey,
    ADD CONSTRAINT song_reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES users (id) ON DELETE SET NULL;
