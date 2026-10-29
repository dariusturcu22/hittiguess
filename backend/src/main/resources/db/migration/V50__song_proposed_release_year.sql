-- Stored when a pipeline rerun on a verified song resolves a different release year.
-- The stored release year stays unchanged until an admin accepts or dismisses the proposal.
ALTER TABLE songs ADD COLUMN proposed_release_year INTEGER;
ALTER TABLE songs ADD COLUMN proposed_at TIMESTAMP WITH TIME ZONE;
