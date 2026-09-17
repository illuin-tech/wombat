-- model_metrics stores LLM output-token usage sampled from Prometheus. Like server_metrics, the
-- source labels (profileId, model) live in a self-describing JSON `data` column so the same table
-- fits future non-Prometheus sources, and are addressed via json_extract. Each row holds the output
-- tokens produced since the previous aggregation (a per-tick delta); summing rows over a range
-- yields total output tokens.
CREATE TABLE model_metrics (
                               id           INTEGER PRIMARY KEY,
                               instantMs    INTEGER NOT NULL,
                               data         TEXT    NOT NULL,
                               outputTokens INTEGER NOT NULL
);

CREATE INDEX idx_model_metrics_profile_instant ON model_metrics (json_extract(data, '$.profileId'), instantMs);
