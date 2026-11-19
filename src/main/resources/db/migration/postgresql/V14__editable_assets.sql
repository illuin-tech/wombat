-- Environments and assets become editable through the API. `provisioned` tells the rows declared by the
-- monitored-environments file or a worker (read-only, reconciled) from those created by users; every existing row
-- comes from a declaration. deleted_at is a soft delete (hidden everywhere), disabled_at an inactive state (skipped
-- by processing, still returned by the API).
ALTER TABLE environments ADD COLUMN description TEXT;
ALTER TABLE environments ADD COLUMN provisioned BIGINT NOT NULL DEFAULT 1;
ALTER TABLE environments ADD COLUMN deleted_at  TIMESTAMP WITH TIME ZONE;

-- Until now disabled_at was only set by the reconciler once nothing declared the environment: a soft delete.
UPDATE environments SET deleted_at = disabled_at, disabled_at = NULL WHERE disabled_at IS NOT NULL;

ALTER TABLE assets ADD COLUMN description TEXT;
ALTER TABLE assets ADD COLUMN provisioned BIGINT NOT NULL DEFAULT 1;
ALTER TABLE assets ADD COLUMN disabled_at TIMESTAMP WITH TIME ZONE;
