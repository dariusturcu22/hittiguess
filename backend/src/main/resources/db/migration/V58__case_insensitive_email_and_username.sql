-- Email addresses and usernames are unique regardless of letter case. Application code now
-- stores and looks up emails in lower case.
--
-- Two accounts whose emails differ only by case cannot be merged or renamed without
-- deciding which person keeps the address, so that case stops the migration with a message
-- instead of picking a winner. Resolve it by hand (change or remove one of the listed
-- accounts) and run the migration again.
DO $$
DECLARE
    duplicate_email_count INTEGER;
    duplicate_account_ids TEXT;
BEGIN
    SELECT count(*), string_agg(account_ids, '; ')
    INTO duplicate_email_count, duplicate_account_ids
    FROM (
        SELECT string_agg(id::text, ', ' ORDER BY id) AS account_ids
        FROM users
        WHERE email IS NOT NULL
        GROUP BY lower(email)
        HAVING count(*) > 1
    ) AS duplicate_groups;

    IF duplicate_email_count > 0 THEN
        RAISE EXCEPTION 'Cannot make emails case-insensitively unique: % groups of accounts share an email that differs only by case. User ids per group: %', duplicate_email_count, duplicate_account_ids;
    END IF;
END $$;

UPDATE users SET email = lower(email) WHERE email IS NOT NULL AND email <> lower(email);

-- A username is a display name and not a login credential, so the later accounts of a
-- case-only duplicate keep working with their id appended to the name.
UPDATE users
SET username = username || '_' || id
WHERE id IN (
    SELECT id FROM (
        SELECT id, row_number() OVER (PARTITION BY lower(username) ORDER BY id) AS occurrence
        FROM users
    ) AS ranked_usernames
    WHERE occurrence > 1
);

CREATE UNIQUE INDEX users_email_lower_unique_index ON users (lower(email));
CREATE UNIQUE INDEX users_username_lower_unique_index ON users (lower(username));
