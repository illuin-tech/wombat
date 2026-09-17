ALTER TABLE datapoint ADD COLUMN cluster   TEXT;
ALTER TABLE datapoint ADD COLUMN namespace TEXT;

DELETE FROM datapoint WHERE type = 'KUBERNETES';

CREATE INDEX idx_datapoint_type_cluster_instant ON datapoint (type, cluster, instantMs);
