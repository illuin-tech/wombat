-- Merge the standalone `profiles` table into each asset's config and add an audit history.
--   * Each asset's referenced profile is folded into its `data` JSON under a `profile` key, so the
--     asset config is now self-contained (impact params live next to the connection config).
--   * `assets` gains created_at/updated_at/deleted_at (soft-delete), mirroring `environments`.
--   * A new append-only `asset_config_history` records every CREATE/UPDATE/DELETE.
--   * `profiles` is dropped.
-- The monitored-environments YAML stays the source of truth and is reconciled into these tables at
-- startup. The inlined profile keys are kebab-case to match the InfrastructureProfile /
-- StaticLLMProfile / DynamicLLMProfile records bound from that YAML.

-- 1) Fold the referenced profile into each asset's data.profile.
UPDATE assets
SET data = json_set(data, '$.profile', json_object(
        'provider', (SELECT p.provider FROM profiles p WHERE p.id = assets.profile_id),
        'instance-type', (SELECT json_extract(p.data, '$.instance_type') FROM profiles p WHERE p.id = assets.profile_id),
        'location', (SELECT p.location FROM profiles p WHERE p.id = assets.profile_id),
        'lifespan', (SELECT json_extract(p.data, '$.lifespan') FROM profiles p WHERE p.id = assets.profile_id)
    ))
WHERE type = 'KUBERNETES_API';

UPDATE assets
SET data = json_set(data, '$.profile', json_object(
        'provider', (SELECT p.provider FROM profiles p WHERE p.id = assets.profile_id),
        'model', (SELECT json_extract(p.data, '$.model') FROM profiles p WHERE p.id = assets.profile_id),
        'location', (SELECT p.location FROM profiles p WHERE p.id = assets.profile_id),
        'request-profile', json_object(
            'output-token-count', (SELECT json_extract(p.data, '$.output_token_count') FROM profiles p WHERE p.id = assets.profile_id),
            'request-per-year', (SELECT json_extract(p.data, '$.request_per_year') FROM profiles p WHERE p.id = assets.profile_id)
        )
    ))
WHERE type = 'LLM_STATIC';

UPDATE assets
SET data = json_set(data, '$.profile', json_object(
        'provider', (SELECT p.provider FROM profiles p WHERE p.id = assets.profile_id),
        'model', (SELECT json_extract(p.data, '$.model') FROM profiles p WHERE p.id = assets.profile_id),
        'location', (SELECT p.location FROM profiles p WHERE p.id = assets.profile_id),
        'dynamic-profile', json_object(
            'query', (SELECT json_extract(p.data, '$.query') FROM profiles p WHERE p.id = assets.profile_id)
        )
    ))
WHERE type = 'LLM_PROMETHEUS';

-- 2) Rebuild `assets` without profile_id and with audit timestamps (SQLite has no clean
--    DROP COLUMN + FK removal, so rebuild the table).
CREATE TABLE assets_new (
    id             TEXT PRIMARY KEY,
    environment_id TEXT NOT NULL REFERENCES environments (id),
    name           TEXT NOT NULL,
    type           TEXT NOT NULL,
    data           TEXT NOT NULL,
    uuid           TEXT,
    created_at     INTEGER NOT NULL,
    updated_at     INTEGER NOT NULL,
    deleted_at     INTEGER
);

INSERT INTO assets_new (id, environment_id, name, type, data, uuid, created_at, updated_at, deleted_at)
    SELECT id, environment_id, name, type, data, uuid,
        CAST(strftime('%s', 'now') AS INTEGER) * 1000,
        CAST(strftime('%s', 'now') AS INTEGER) * 1000,
        NULL
    FROM assets;

DROP TABLE assets;
ALTER TABLE assets_new RENAME TO assets;

CREATE INDEX idx_assets_environment ON assets (environment_id);
CREATE UNIQUE INDEX idx_assets_uuid ON assets (uuid);

-- 3) Profiles are fully inlined now.
DROP TABLE profiles;

-- 4) Append-only audit trail of asset config changes. asset_id is intentionally NOT a foreign key:
--    history must survive asset deletion.
CREATE TABLE asset_config_history (
    id             INTEGER PRIMARY KEY,
    asset_id       TEXT    NOT NULL,
    environment_id TEXT    NOT NULL,
    action         TEXT    NOT NULL,
    changed_at     INTEGER NOT NULL,
    snapshot       TEXT    NOT NULL
);

CREATE INDEX idx_asset_config_history_asset ON asset_config_history (asset_id, changed_at);
