package tech.illuin.wombat.persistence.backend.postgres;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Reports the on-disk size of the connected database. The SQLite counterpart multiplies its
 * PRAGMA page count by the page size; Postgres exposes the same figure directly, covering the
 * whole database (tables, indexes and their TOAST storage).
 */
public class PostgresSizeGauge
{
    private static final String SIZE_QUERY = "SELECT pg_database_size(current_database())";

    private final DataSource dataSource;

    public PostgresSizeGauge(DataSource dataSource)
    {
        this.dataSource = dataSource;
    }

    public double sizeBytes()
    {
        try (Connection conn = this.dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SIZE_QUERY))
        {
            return rs.next() ? rs.getLong(1) : 0;
        }
        catch (SQLException e) {
            return -1;
        }
    }
}
