-- When a member's socket dropped, so the voice sweep can remove someone who closed their
-- tab without leaving the call. Null while the member is connected.
ALTER TABLE members ADD COLUMN disconnected_at TIMESTAMP;
