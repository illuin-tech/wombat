-- Rebuild datapoint with one row per container CPU sample. Previously a single row
-- per cluster/namespace held a JSON payload of {"pods":{pod:{"containers":{container:cpu}}}};
-- here we explode that payload into individual pod/container/cpu rows so the existing
-- database is migrated in place rather than recreated.

CREATE TABLE datapoint_new
(
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    instantMs INTEGER NOT NULL,
    type      TEXT    NOT NULL,
    cluster   TEXT,
    namespace TEXT,
    pod       TEXT    NOT NULL,
    container TEXT    NOT NULL,
    cpu       REAL    NOT NULL
);

INSERT INTO datapoint_new (instantMs, type, cluster, namespace, pod, container, cpu)
SELECT d.instantMs,
       d.type,
       d.cluster,
       d.namespace,
       pod.key,
       cont.key,
       CAST(cont.value AS REAL)
FROM datapoint d,
     json_each(json_extract(d.payload, '$.pods')) AS pod,
     json_each(json_extract(pod.value, '$.containers')) AS cont;

DROP TABLE datapoint;
ALTER TABLE datapoint_new RENAME TO datapoint;

CREATE INDEX idx_datapoint_type_cluster_instant ON datapoint (type, cluster, instantMs);
