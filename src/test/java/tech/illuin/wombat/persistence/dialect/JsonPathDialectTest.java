package tech.illuin.wombat.persistence.dialect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The rendered fragments are asserted literally because they are concatenated into native SQL: a
 * silent change here would produce a query that only fails against a live database.
 */
class JsonPathDialectTest
{
    @Test
    void sqlite_appliesTheOperatorStraightToTheTextColumn()
    {
        assertEquals("data ->> 'cluster'", JsonPathDialect.SQLITE.text("data", "cluster"));
    }

    @Test
    void postgresql_castsTheTextColumnToJsonbFirst()
    {
        assertEquals("data::jsonb ->> 'cluster'", JsonPathDialect.POSTGRESQL.text("data", "cluster"));
    }

    @Test
    void bothDialects_renderTheColumnAndKeyTheyAreGiven()
    {
        assertEquals("payload ->> 'profileId'", JsonPathDialect.SQLITE.text("payload", "profileId"));
        assertEquals("payload::jsonb ->> 'profileId'", JsonPathDialect.POSTGRESQL.text("payload", "profileId"));
    }
}
