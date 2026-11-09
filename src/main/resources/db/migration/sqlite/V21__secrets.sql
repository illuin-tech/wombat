CREATE TABLE secrets
(
    secret_key         TEXT PRIMARY KEY NOT NULL,
    payload_ciphertext BLOB NOT NULL,
    payload_nonce      BLOB NOT NULL,
    wrapped_dek        BLOB NOT NULL,
    wrapping_nonce     BLOB NOT NULL,
    kek_id             TEXT NOT NULL,
    created_at         INTEGER NOT NULL,
    updated_at         INTEGER NOT NULL,
    revision           INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_secrets_kek_id ON secrets (kek_id);