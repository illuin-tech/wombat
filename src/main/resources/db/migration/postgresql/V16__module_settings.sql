CREATE TABLE modules
(
    type_name  TEXT PRIMARY KEY,
    namespace  TEXT NOT NULL,
    local_name TEXT NOT NULL,
    regime     TEXT NOT NULL,
    family     TEXT NOT NULL,
    enabled    BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by TEXT
);

CREATE TABLE core_deployments
(
    version         BIGINT PRIMARY KEY,
    id              TEXT NOT NULL,
    requested_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    requested_by    TEXT,
    enabled_modules TEXT NOT NULL
);

CREATE UNIQUE INDEX idx_core_deployments_id ON core_deployments (id);

CREATE TABLE core_instances
(
    instance_id      TEXT PRIMARY KEY,
    deployed_version BIGINT NOT NULL,
    deployed_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    status           TEXT NOT NULL,
    error            TEXT,
    detected_modules TEXT NOT NULL,
    missing_modules  TEXT NOT NULL,
    heartbeat_at     TIMESTAMP WITH TIME ZONE NOT NULL
);
