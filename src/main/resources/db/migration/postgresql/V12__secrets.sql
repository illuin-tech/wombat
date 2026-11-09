CREATE TABLE secrets
(
    secret_key         TEXT PRIMARY KEY,
    payload_ciphertext BYTEA NOT NULL,
    payload_nonce      BYTEA NOT NULL,
    wrapped_dek        BYTEA NOT NULL,
    wrapping_nonce     BYTEA NOT NULL,
    kek_id             TEXT NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    revision           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_secrets_kek_id ON secrets (kek_id);