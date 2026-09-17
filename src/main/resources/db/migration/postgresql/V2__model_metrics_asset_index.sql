-- Mirrors the SQLite chain: the `data` payload is an LLMData keyed by serviceId, assetId,
-- environmentId, model and outputTokens, so the V1 index on `profileId` addressed a key that never
-- exists. Impacts are summed for one asset over a range, hence `assetId`.
DROP INDEX IF EXISTS idx_model_metrics_profile_instant;

CREATE INDEX idx_model_metrics_asset_instant ON model_metrics ((data::jsonb ->> 'assetId'), instantMs);
