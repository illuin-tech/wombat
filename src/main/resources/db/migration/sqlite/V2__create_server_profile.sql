CREATE TABLE server_profile (
    id           TEXT    PRIMARY KEY,
    description  TEXT    NOT NULL,
    provider     TEXT    NOT NULL,
    instanceType TEXT    NOT NULL,
    location     TEXT    NOT NULL,
    lifespan     INTEGER NOT NULL
);
