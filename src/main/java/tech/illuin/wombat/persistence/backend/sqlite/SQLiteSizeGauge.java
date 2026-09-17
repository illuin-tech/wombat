package tech.illuin.wombat.persistence.backend.sqlite;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteSizeGauge
{
    private final DataSource dataSource;

    public SQLiteSizeGauge(DataSource dataSource)
    {
        this.dataSource = dataSource;
    }

    public double sizeBytes()
    {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement())
        {
            long pageCount = pragma(stmt, "page_count");
            long pageSize = pragma(stmt, "page_size");
            return pageCount * pageSize;
        }
        catch (SQLException e) {
            return -1;
        }
    }

    private static long pragma(Statement stmt, String name) throws SQLException
    {
        try (ResultSet rs = stmt.executeQuery("PRAGMA " + name))
        {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }
}
