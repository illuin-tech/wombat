package tech.illuin.wombat.persistence.dialect;

/**
 * Renders a JSON text extraction over a TEXT payload column, per database backend.
 * <p>
 * The self-describing {@code data} columns (server_metrics, model_metrics) are stored as plain JSON
 * strings, and the native queries that filter or group on their keys are the only SQL in the app
 * that cannot be written once for every backend. SQLite (>= 3.38) and Postgres share the
 * {@code ->>} operator, so the whole difference is the cast Postgres needs to reach it.
 * <p>
 * {@code assets.properties} holds JSON too, but is only ever read whole through the AssetConverter,
 * so no dialect-specific extraction is needed for it.
 * <p>
 * Keys are expected to be compile-time constants — they are interpolated into the SQL as-is.
 */
public interface JsonPathDialect
{
    /** SQLite applies {@code ->>} straight to the TEXT column, a bare key meaning {@code $."key"}. */
    JsonPathDialect SQLITE = (column, key) -> column + " ->> '" + key + "'";

    /** Postgres only defines {@code ->>} on json/jsonb, so the TEXT payload is cast first. */
    JsonPathDialect POSTGRESQL = (column, key) -> column + "::jsonb ->> '" + key + "'";

    /**
     * @param column the TEXT column holding the JSON payload
     * @param key    a top-level key of that payload
     * @return an SQL expression yielding the key's value as text, NULL when absent
     */
    String text(String column, String key);
}
