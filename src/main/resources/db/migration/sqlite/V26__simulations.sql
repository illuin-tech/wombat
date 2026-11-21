-- Sandboxes of asset definitions, evaluated on demand and never monitored. Hard-deleted, and not tied to their source
-- environment by a foreign key: environments are only soft-deleted, and a simulation outlives its source environment.
CREATE TABLE simulations
(
    id                    TEXT PRIMARY KEY,
    name                  TEXT NOT NULL,
    description           TEXT,
    notes                 TEXT,
    reference_window      TEXT NOT NULL DEFAULT 'MONTH',
    source_environment_id TEXT,
    created_at            INTEGER NOT NULL,
    updated_at            INTEGER NOT NULL
);

-- Ids are unique within their simulation only, so that a duplicate keeps them. The source is the asset a definition was
-- copied from, in any environment; it is not a foreign key either, the copy outlives it. SQLite does not enforce the
-- cascade (foreign keys are off): the app deletes a simulation's assets itself.
CREATE TABLE simulation_assets
(
    simulation_id         TEXT NOT NULL REFERENCES simulations (id) ON DELETE CASCADE,
    id                    TEXT NOT NULL,
    name                  TEXT NOT NULL,
    description           TEXT,
    notes                 TEXT,
    source_asset_id       TEXT,
    source_environment_id TEXT,
    type                  TEXT NOT NULL,
    properties            TEXT NOT NULL,
    synced_at             INTEGER,
    created_at            INTEGER NOT NULL,
    updated_at            INTEGER NOT NULL,
    PRIMARY KEY (simulation_id, id)
);
