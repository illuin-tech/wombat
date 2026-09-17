-- Consolidated ecologits schema migration (formerly V8..V16, squashed into one step on the
-- mvp1/ecologits branch). Applied on top of the V7 schema it produces the exact same final state:
--   * kubernetes_metrics -> server_metrics with a self-describing JSON `data` column
--   * server_profile     -> polymorphic `profiles` with a JSON `data` payload
--   * new `environments` and `assets` tables
--   * a UUID technical id on profiles/environments/assets
-- Data held in the pre-V7 tables is preserved; the empty tables are seeded by the app at startup.

-- kubernetes_metrics -> server_metrics: the source labels (cluster, namespace, pod, container)
-- move into a self-describing JSON `data` column so future non-Kubernetes sources fit the same
-- table. The container metric source is discriminated as KUBERNETES_API.
CREATE TABLE server_metrics (
    id            INTEGER PRIMARY KEY,
    instantMs     INTEGER NOT NULL,
    data          TEXT    NOT NULL,
    cpu_nanocores REAL    NOT NULL,
    ram_bytes     REAL    NOT NULL
);

INSERT INTO server_metrics (id, instantMs, data, cpu_nanocores, ram_bytes)
    SELECT id, instantMs,
        json_object(
            'type', 'KUBERNETES_API',
            'cluster', cluster,
            'namespace', namespace,
            'pod', pod,
            'container', container
        ),
        cpu_nanocores, ram_bytes
    FROM kubernetes_metrics;

DROP TABLE kubernetes_metrics;

CREATE INDEX idx_server_metrics_cluster_instant ON server_metrics (json_extract(data, '$.cluster'), instantMs);

-- server_profile -> profiles: only the fields shared by every profile kind stay dedicated columns;
-- the type-specific payload (INFRASTRUCTURE: instance_type/lifespan, LLM: model/output_token_count/
-- request_per_year) moves into a self-describing JSON `data` column whose `type` discriminator
-- mirrors the type column. Pre-existing rows are all INFRASTRUCTURE.
CREATE TABLE profiles (
    id          TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    type        TEXT NOT NULL,
    provider    TEXT NOT NULL,
    location    TEXT NOT NULL,
    data        TEXT NOT NULL,
    uuid        TEXT
);

INSERT INTO profiles (id, description, type, provider, location, data, uuid)
    SELECT id, description, 'KUBERNETES_API', provider, location,
        json_object(
            'type', 'KUBERNETES_API',
            'instance_type', instanceType,
            'lifespan', lifespan
        ),
        lower(
            hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2)
            || '-' || substr('89ab', (abs(random()) % 4) + 1, 1) || substr(hex(randomblob(2)), 2)
            || '-' || hex(randomblob(6))
        )
    FROM server_profile;

DROP TABLE server_profile;

CREATE UNIQUE INDEX idx_profiles_uuid ON profiles (uuid);

-- Environments become persistent (they previously only existed in the monitored-environments
-- config, which stays the source of truth for asset definitions). Rows are seeded at startup and
-- act as an activity registry: the app only uses environments whose disabled_at is NULL.
-- Timestamps are epoch millis.
CREATE TABLE environments (
    id          TEXT    PRIMARY KEY,
    name        TEXT    NOT NULL,
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL,
    disabled_at INTEGER,
    uuid        TEXT
);

CREATE UNIQUE INDEX idx_environments_uuid ON environments (uuid);

-- Assets become persistent so the environments table holds the full environment configuration.
-- Rows are seeded at startup from the monitored-environments config and manageable through the
-- /environments REST endpoints. Like profiles, only the fields shared by every asset kind are
-- dedicated columns; the type-specific payload (KUBERNETES_API: config_path/namespace/context/
-- read_timeout, LLM_STATIC: none) lives in a self-describing JSON `data` column whose `type`
-- discriminator mirrors the type column.
CREATE TABLE assets (
    id             TEXT PRIMARY KEY,
    environment_id TEXT NOT NULL REFERENCES environments (id),
    name           TEXT NOT NULL,
    type           TEXT NOT NULL,
    profile_id     TEXT NOT NULL REFERENCES profiles (id),
    data           TEXT NOT NULL,
    uuid           TEXT
);

CREATE INDEX idx_assets_environment ON assets (environment_id);
CREATE UNIQUE INDEX idx_assets_uuid ON assets (uuid);
