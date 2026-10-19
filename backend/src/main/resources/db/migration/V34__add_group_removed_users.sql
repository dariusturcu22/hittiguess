-- Users an admin removed from a group. They can't rejoin that group for as long as it lives.
CREATE TABLE group_removed_users (
    group_id BIGINT NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (group_id, user_id)
);
