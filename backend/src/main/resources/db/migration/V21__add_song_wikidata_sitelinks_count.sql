-- Wikidata sitelinks count per song (story 30's cold-start popularity proxy). Nullable
-- with no default: a null means the count is unknown (song resolved before capture existed,
-- or the entity lookup failed), and difficulty scoring treats unknown as neutral rather
-- than guessing well-known or obscure.
ALTER TABLE songs ADD COLUMN wikidata_sitelinks_count INTEGER;
