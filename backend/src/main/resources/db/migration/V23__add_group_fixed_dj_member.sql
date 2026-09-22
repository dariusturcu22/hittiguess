-- Fixed-DJ member choice per group (story 47 lobby ground truth). Nullable with no
-- default: null keeps the historical earliest-joined-member behavior. Deliberately
-- no foreign key to members: member rows are removed through orphan removal, and a
-- stale id falls back the same way at session start instead of blocking the delete.
ALTER TABLE groups ADD COLUMN fixed_dj_member_id BIGINT;
