-- A module either defines an asset-type, or serves a whole family without one: the latter has no activity regime.
-- Existing rows are asset-type modules, and keep their name, state and author. SQLite cannot drop a NOT NULL
-- constraint, hence the rebuild.
CREATE TABLE modules_new
(
    type_name  TEXT PRIMARY KEY,
    kind       TEXT NOT NULL DEFAULT 'asset',
    namespace  TEXT NOT NULL,
    local_name TEXT NOT NULL,
    regime     TEXT,
    family     TEXT NOT NULL,
    enabled    INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    updated_by TEXT
);

INSERT INTO modules_new (type_name, kind, namespace, local_name, regime, family, enabled, updated_at, updated_by)
    SELECT type_name, 'asset', namespace, local_name, regime, family, enabled, updated_at, updated_by
    FROM modules;

DROP TABLE modules;

ALTER TABLE modules_new RENAME TO modules;
