ALTER TABLE server_metrics RENAME COLUMN instantMs TO instant_ms;

ALTER TABLE model_metrics RENAME COLUMN instantMs TO instant_ms;
ALTER TABLE model_metrics RENAME COLUMN outputTokens TO output_tokens;
