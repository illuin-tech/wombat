package tech.illuin.wombat.persistence.backend.postgres;

import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One Postgres server shared by every test class here, following the Testcontainers singleton
 * pattern: startup is paid once and Ryuk reaps the container when the JVM exits. Each test class
 * asks for its own database so migrating or writing rows in one cannot perturb another.
 */
public final class PostgresTestContainer
{
    /** Pinned to the image compose.yaml runs, so tests and local dev exercise the same server. */
    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:18.1-alpine");

    private static PostgreSQLContainer<?> container;

    private PostgresTestContainer()
    {
    }

    public static synchronized PostgreSQLContainer<?> get()
    {
        if (container == null)
        {
            container = new PostgreSQLContainer<>(IMAGE);
            container.start();
        }
        return container;
    }

    /**
     * Creates (or recreates) an empty database and returns its JDBC url. The url is assembled from
     * the mapped port rather than rewritten out of {@code getJdbcUrl()}, whose query parameters make
     * string surgery brittle.
     */
    public static String freshDatabase(String name) throws SQLException
    {
        PostgreSQLContainer<?> postgres = get();
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement())
        {
            stmt.execute("DROP DATABASE IF EXISTS " + name);
            stmt.execute("CREATE DATABASE " + name);
        }
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + name;
    }

    public static Connection connect(String jdbcUrl) throws SQLException
    {
        PostgreSQLContainer<?> postgres = get();
        return DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
    }

    public static DataSource dataSource(String jdbcUrl)
    {
        PostgreSQLContainer<?> postgres = get();
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(jdbcUrl);
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }

    public static String username()
    {
        return get().getUsername();
    }

    public static String password()
    {
        return get().getPassword();
    }
}
