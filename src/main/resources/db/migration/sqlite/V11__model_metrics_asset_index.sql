-- The `data` payload is an LLMData, whose keys are serviceId, assetId, environmentId, model and
-- outputTokens — there is no `profileId`, so the V9 index addressed a key that never exists and the
-- lookup it backed always matched nothing. Impacts are summed for one asset over a range
-- (LLMImpactResolver passes Asset.id()), so both the filter and its index address `assetId`.
--
-- The expression is written exactly as JsonPathDialect.SQLITE renders it (`->>`). SQLite only
-- applies an expression index to an equality when the two expressions match textually: indexing
-- json_extract(data, '$.assetId') while querying `data ->> 'assetId'` still uses the index, but as
-- ANY(<expr>) — every index entry is walked and only the instantMs range constrains the search.
DROP INDEX IF EXISTS idx_model_metrics_profile_instant;
DROP INDEX IF EXISTS idx_model_metrics_asset_instant;

CREATE INDEX idx_model_metrics_asset_instant ON model_metrics (data ->> 'assetId', instantMs);
