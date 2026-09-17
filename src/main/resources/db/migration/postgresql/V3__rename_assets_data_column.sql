-- Mirrors the SQLite chain: the column holds the asset definition itself, so it follows AssetEntity.properties.
ALTER TABLE assets RENAME COLUMN data TO properties;
