-- An email change is stored on the account's verification token and only replaces
-- users.email once the link sent to the new address is opened.
ALTER TABLE email_verification_tokens ADD COLUMN pending_email VARCHAR(255);
