ALTER TABLE environments ADD COLUMN description TEXT;
ALTER TABLE environments ADD COLUMN provisioned INTEGER NOT NULL DEFAULT 1;
ALTER TABLE environments ADD COLUMN deleted_at  INTEGER;

UPDATE environments SET deleted_at = disabled_at, disabled_at = NULL WHERE disabled_at IS NOT NULL;

ALTER TABLE assets ADD COLUMN description TEXT;
ALTER TABLE assets ADD COLUMN provisioned INTEGER NOT NULL DEFAULT 1;
ALTER TABLE assets ADD COLUMN disabled_at INTEGER;
