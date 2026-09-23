-- Prometheus credentials are now sourced from the environment: LLMPrometheusAsset carries `password-key`, the name
-- of the variable holding the password, never the password itself. Assets are persisted verbatim, so any value an
-- earlier build captured is still readable here and in the config-history snapshots — remove it rather than leave
-- it behind, and drop the key so rows deserialize against the current record.
UPDATE assets
   SET properties = json_remove(properties, '$.password')
 WHERE properties LIKE '%"password"%';

-- The history snapshot nests the asset under `data`.
UPDATE asset_config_history
   SET snapshot = json_remove(snapshot, '$.data.password')
 WHERE snapshot LIKE '%"password"%';
