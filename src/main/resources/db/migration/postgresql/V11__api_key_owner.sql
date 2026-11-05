ALTER TABLE api_keys RENAME COLUMN created_by TO owner_id;

CREATE INDEX idx_api_keys_owner ON api_keys (owner_id);
