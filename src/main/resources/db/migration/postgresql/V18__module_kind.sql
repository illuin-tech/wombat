-- A module either defines an asset-type, or serves a whole family without one: the latter has no activity regime.
-- Existing rows are asset-type modules, and keep their name, state and author.
ALTER TABLE modules ADD COLUMN kind TEXT NOT NULL DEFAULT 'asset';
ALTER TABLE modules ALTER COLUMN regime DROP NOT NULL;
