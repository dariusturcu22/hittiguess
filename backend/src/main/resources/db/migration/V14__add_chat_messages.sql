-- Group-scoped text chat (story 13). A chat message belongs to one group and one sender,
-- lives for the whole life of the group, and is removed with the group: the foreign key to
-- groups cascades on delete so the scheduled expiry sweep and an admin's final leave both
-- clear a group's messages along with the group itself, matching the group's ephemeral
-- lifecycle. The sender foreign key does not cascade, since a message stays attributable
-- for as long as its group exists.
CREATE TABLE chat_messages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    sender_id BIGINT NOT NULL REFERENCES users (id),
    content VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- History loads a group's most recent messages in one bounded, time-ordered read, so the
-- group foreign key carries an index over the created-at ordering.
CREATE INDEX idx_chat_messages_group_id_created_at ON chat_messages (group_id, created_at);
