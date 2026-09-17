-- Mirrors the SQLite chain: strip any Prometheus password captured before credentials moved to the environment.
-- jsonb_exists() rather than the `?` operator, which a JDBC driver would read as a bind placeholder.
UPDATE assets
   SET properties = (properties::jsonb - 'password')::text
 WHERE jsonb_exists(properties::jsonb, 'password');

-- The history snapshot nests the asset under `data`.
UPDATE asset_config_history
   SET snapshot = (snapshot::jsonb #- '{data,password}')::text
 WHERE jsonb_exists(snapshot::jsonb -> 'data', 'password');
