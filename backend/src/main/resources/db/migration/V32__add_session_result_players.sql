-- The users who played the session a group's stored results belong to. Only they can read
-- those results, since group ids are sequential and guessable.
CREATE TABLE session_result_players (
    group_id BIGINT NOT NULL REFERENCES session_results (group_id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (group_id, user_id)
);
