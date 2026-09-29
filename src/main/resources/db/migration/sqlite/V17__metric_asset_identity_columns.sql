ALTER TABLE server_metrics ADD COLUMN asset_id       TEXT;
ALTER TABLE server_metrics ADD COLUMN environment_id TEXT;
ALTER TABLE server_metrics ADD COLUMN asset_name     TEXT;
ALTER TABLE server_metrics ADD COLUMN asset_type     TEXT;

ALTER TABLE model_metrics ADD COLUMN asset_id       TEXT;
ALTER TABLE model_metrics ADD COLUMN environment_id TEXT;
ALTER TABLE model_metrics ADD COLUMN asset_name     TEXT;
ALTER TABLE model_metrics ADD COLUMN asset_type     TEXT;

UPDATE server_metrics
   SET asset_id       = COALESCE(data ->> 'assetId', data ->> 'cluster', 'unknown'),
       environment_id = COALESCE(data ->> 'environmentId', 'unknown'),
       asset_name     = COALESCE(
           (SELECT a.name FROM assets a WHERE a.id = data ->> 'assetId'),
           data ->> 'assetId',
           data ->> 'cluster',
           'unknown'
       ),
       asset_type     = COALESCE(
           (SELECT a.type FROM assets a WHERE a.id = data ->> 'assetId'),
           'tech.illuin.wombat-core.unknown'
       );

UPDATE model_metrics
   SET asset_id       = COALESCE(data ->> 'assetId', 'unknown'),
       environment_id = COALESCE(data ->> 'environmentId', 'unknown'),
       asset_name     = COALESCE(
           (SELECT a.name FROM assets a WHERE a.id = data ->> 'assetId'),
           data ->> 'assetId',
           'unknown'
       ),
       asset_type     = COALESCE(
           (SELECT a.type FROM assets a WHERE a.id = data ->> 'assetId'),
           'tech.illuin.wombat-core.unknown'
       );

UPDATE server_metrics SET data = json_remove(data, '$.assetId', '$.environmentId');
UPDATE model_metrics  SET data = json_remove(data, '$.assetId', '$.environmentId');

CREATE TABLE server_metrics_new (
    id             INTEGER PRIMARY KEY,
    instant_ms     INTEGER NOT NULL,
    window_ms      INTEGER NOT NULL DEFAULT 300000,
    asset_id       TEXT    NOT NULL,
    environment_id TEXT    NOT NULL,
    asset_name     TEXT    NOT NULL,
    asset_type     TEXT    NOT NULL,
    data           TEXT    NOT NULL,
    cpu_nanocores  REAL    NOT NULL,
    ram_bytes      REAL    NOT NULL,
    compacted      INTEGER NOT NULL DEFAULT 0
);

INSERT INTO server_metrics_new (id, instant_ms, window_ms, asset_id, environment_id, asset_name, asset_type, data, cpu_nanocores, ram_bytes, compacted)
    SELECT id, instant_ms, window_ms, asset_id, environment_id, asset_name, asset_type, data, cpu_nanocores, ram_bytes, compacted
    FROM server_metrics;

DROP TABLE server_metrics;

ALTER TABLE server_metrics_new RENAME TO server_metrics;

CREATE TABLE model_metrics_new (
    id             INTEGER PRIMARY KEY,
    instant_ms     INTEGER NOT NULL,
    asset_id       TEXT    NOT NULL,
    environment_id TEXT    NOT NULL,
    asset_name     TEXT    NOT NULL,
    asset_type     TEXT    NOT NULL,
    data           TEXT    NOT NULL,
    output_tokens  INTEGER NOT NULL,
    compacted      INTEGER NOT NULL DEFAULT 0
);

INSERT INTO model_metrics_new (id, instant_ms, asset_id, environment_id, asset_name, asset_type, data, output_tokens, compacted)
    SELECT id, instant_ms, asset_id, environment_id, asset_name, asset_type, data, output_tokens, compacted
    FROM model_metrics;

DROP TABLE model_metrics;

ALTER TABLE model_metrics_new RENAME TO model_metrics;

CREATE INDEX idx_server_metrics_asset_instant ON server_metrics (asset_id, instant_ms);
CREATE INDEX idx_model_metrics_asset_instant ON model_metrics (asset_id, instant_ms);
