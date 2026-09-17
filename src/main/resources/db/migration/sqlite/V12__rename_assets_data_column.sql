-- The column holds the asset definition itself (an Asset payload), not an opaque `data` blob like the metric
-- tables do, so it follows AssetEntity.properties. Nothing queries it through a JSON path — the asset rows are
-- only ever read whole, via the AssetConverter — so no index or native query has to follow.
ALTER TABLE assets RENAME COLUMN data TO properties;
