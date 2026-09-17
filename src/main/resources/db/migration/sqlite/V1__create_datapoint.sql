CREATE TABLE datapoint (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    instantMs INTEGER NOT NULL,
    type      TEXT    NOT NULL,
    payload   TEXT    NOT NULL
);

CREATE INDEX idx_datapoint_instant_type ON datapoint (instantMs, type);