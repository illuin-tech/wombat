-- Make the units explicit in the column names.
ALTER TABLE kubernetes_metrics RENAME COLUMN cpu TO cpu_nanocores;
ALTER TABLE kubernetes_metrics RENAME COLUMN ram TO ram_bytes;
