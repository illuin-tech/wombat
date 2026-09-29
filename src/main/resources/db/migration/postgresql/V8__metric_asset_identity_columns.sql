ALTER TABLE server_metrics ADD COLUMN asset_id       TEXT;
ALTER TABLE server_metrics ADD COLUMN environment_id TEXT;
ALTER TABLE server_metrics ADD COLUMN asset_name     TEXT;
ALTER TABLE server_metrics ADD COLUMN asset_type     TEXT;

ALTER TABLE model_metrics ADD COLUMN asset_id       TEXT;
ALTER TABLE model_metrics ADD COLUMN environment_id TEXT;
ALTER TABLE model_metrics ADD COLUMN asset_name     TEXT;
ALTER TABLE model_metrics ADD COLUMN asset_type     TEXT;

UPDATE server_metrics
   SET asset_id       = COALESCE(data::jsonb ->> 'assetId', data::jsonb ->> 'cluster', 'unknown'),
       environment_id = COALESCE(data::jsonb ->> 'environmentId', 'unknown'),
       asset_name     = COALESCE(
           (SELECT a.name FROM assets a WHERE a.id = data::jsonb ->> 'assetId'),
           data::jsonb ->> 'assetId',
           data::jsonb ->> 'cluster',
           'unknown'
       ),
       asset_type     = COALESCE(
           (SELECT a.type FROM assets a WHERE a.id = data::jsonb ->> 'assetId'),
           'tech.illuin.wombat-core.unknown'
       );

UPDATE model_metrics
   SET asset_id       = COALESCE(data::jsonb ->> 'assetId', 'unknown'),
       environment_id = COALESCE(data::jsonb ->> 'environmentId', 'unknown'),
       asset_name     = COALESCE(
           (SELECT a.name FROM assets a WHERE a.id = data::jsonb ->> 'assetId'),
           data::jsonb ->> 'assetId',
           'unknown'
       ),
       asset_type     = COALESCE(
           (SELECT a.type FROM assets a WHERE a.id = data::jsonb ->> 'assetId'),
           'tech.illuin.wombat-core.unknown'
       );

UPDATE server_metrics SET data = (data::jsonb - 'assetId' - 'environmentId')::text;
UPDATE model_metrics  SET data = (data::jsonb - 'assetId' - 'environmentId')::text;

ALTER TABLE server_metrics ALTER COLUMN asset_id       SET NOT NULL;
ALTER TABLE server_metrics ALTER COLUMN environment_id SET NOT NULL;
ALTER TABLE server_metrics ALTER COLUMN asset_name     SET NOT NULL;
ALTER TABLE server_metrics ALTER COLUMN asset_type     SET NOT NULL;

ALTER TABLE model_metrics ALTER COLUMN asset_id       SET NOT NULL;
ALTER TABLE model_metrics ALTER COLUMN environment_id SET NOT NULL;
ALTER TABLE model_metrics ALTER COLUMN asset_name     SET NOT NULL;
ALTER TABLE model_metrics ALTER COLUMN asset_type     SET NOT NULL;

-- Both lookups scope to an asset over an instant range, and both now index a plain column instead of a jsonb
-- extraction. The server-metrics filter used to address the payload's `cluster` key, which only matched because the
-- Kubernetes source writes the asset id there.
DROP INDEX IF EXISTS idx_server_metrics_cluster_instant;
DROP INDEX IF EXISTS idx_model_metrics_asset_instant;

CREATE INDEX idx_server_metrics_asset_instant ON server_metrics (asset_id, instant_ms);
CREATE INDEX idx_model_metrics_asset_instant ON model_metrics (asset_id, instant_ms);
