CREATE TABLE users (
    id           TEXT PRIMARY KEY,
    issuer       TEXT                     NOT NULL,
    subject      TEXT                     NOT NULL,
    name         TEXT,
    email        TEXT,
    roles        TEXT                     NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX idx_users_identity ON users (issuer, subject);

CREATE TABLE api_keys (
    id           TEXT PRIMARY KEY,
    name         TEXT                     NOT NULL,
    key_hash     TEXT                     NOT NULL,
    permissions  TEXT                     NOT NULL,
    created_by   TEXT                     NOT NULL REFERENCES users (id),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at   TIMESTAMP WITH TIME ZONE,
    revoked_at   TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX idx_api_keys_key_hash ON api_keys (key_hash);

CREATE TABLE role_grants (
    id         TEXT PRIMARY KEY,
    user_id    TEXT                     NOT NULL REFERENCES users (id),
    role       TEXT                     NOT NULL,
    granted_by TEXT                     NOT NULL REFERENCES users (id),
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX idx_role_grants_user_role ON role_grants (user_id, role);
