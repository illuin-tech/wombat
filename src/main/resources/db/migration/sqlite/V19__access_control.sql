CREATE TABLE users (
    id           TEXT PRIMARY KEY,
    issuer       TEXT    NOT NULL,
    subject      TEXT    NOT NULL,
    name         TEXT,
    email        TEXT,
    roles        TEXT    NOT NULL,
    created_at   INTEGER NOT NULL,
    updated_at   INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL
);

CREATE UNIQUE INDEX idx_users_identity ON users (issuer, subject);

CREATE TABLE api_keys (
    id           TEXT PRIMARY KEY,
    name         TEXT    NOT NULL,
    permissions  TEXT    NOT NULL,
    created_by   TEXT    NOT NULL REFERENCES users (id),
    created_at   INTEGER NOT NULL,
    expires_at   INTEGER,
    revoked_at   INTEGER,
    last_used_at INTEGER
);

CREATE TABLE role_grants (
    id         TEXT PRIMARY KEY,
    user_id    TEXT    NOT NULL REFERENCES users (id),
    role       TEXT    NOT NULL,
    granted_by TEXT    NOT NULL REFERENCES users (id),
    granted_at INTEGER NOT NULL
);

CREATE UNIQUE INDEX idx_role_grants_user_role ON role_grants (user_id, role);
