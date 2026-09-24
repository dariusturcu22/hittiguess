-- How many new songs each user has had resolved through the paid metadata pipeline on a
-- given UTC day, so imports can be held to a daily limit per user.
CREATE TABLE import_quota_usage (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    resolution_count INT NOT NULL,
    PRIMARY KEY (user_id, usage_date)
);
