-- The datapoint table now only ever holds Kubernetes container CPU metrics, so the
-- generic `type` discriminator is dropped and the table is renamed to its domain.
-- Other datasources will live in their own dedicated tables.

DROP INDEX idx_datapoint_type_cluster_instant;

ALTER TABLE datapoint DROP COLUMN type;

ALTER TABLE datapoint RENAME TO kubernetes_metrics;

CREATE INDEX idx_kubernetes_metrics_cluster_instant ON kubernetes_metrics (cluster, instantMs);
