-- Record per-container memory (RAM, in bytes) alongside CPU. Existing rows predate RAM
-- collection, so they default to 0.
ALTER TABLE kubernetes_metrics ADD COLUMN ram REAL NOT NULL DEFAULT 0;
