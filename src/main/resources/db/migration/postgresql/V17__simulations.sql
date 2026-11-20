-- Sandboxes of asset definitions, evaluated on demand and never monitored. Hard-deleted, and not tied to their source
-- environment by a foreign key: environments are only soft-deleted, and a simulation outlives its source.
CREATE TABLE simulations
(
    id                    TEXT PRIMARY KEY,
    name                  TEXT NOT NULL,
    description           TEXT,
    notes                 TEXT,
    reference_window      TEXT NOT NULL DEFAULT 'MONTH',
    parent_environment_id TEXT,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Ids are unique within their simulation only, so that a duplicate keeps them. The parent is the asset a definition was
-- copied from, in any environment; it is not a foreign key either, the copy outlives it.
CREATE TABLE simulation_assets
(
    simulation_id         TEXT NOT NULL REFERENCES simulations (id) ON DELETE CASCADE,
    id                    TEXT NOT NULL,
    name                  TEXT NOT NULL,
    description           TEXT,
    notes                 TEXT,
    parent_asset_id       TEXT,
    parent_environment_id TEXT,
    type                  TEXT NOT NULL,
    properties            TEXT NOT NULL,
    synced_at             TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (simulation_id, id)
);
