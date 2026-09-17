package tech.illuin.wombat.persistence.backend.sqlite.action;

import io.agroal.api.AgroalDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tech.illuin.wombat.persistence.backend.api.Action;
import tech.illuin.wombat.persistence.backend.s3.S3Helper;
import tech.illuin.wombat.persistence.backend.s3.S3Properties;
import tech.illuin.wombat.persistence.backend.api.backup.BackupProducer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class SQLiteBackupProduce implements BackupProducer, Action, AutoCloseable
{
    private final AgroalDataSource dataSource;
    private final S3Properties s3Properties;
    private final S3Client s3Client;

    private static final Logger logger = LoggerFactory.getLogger(SQLiteBackupProduce.class);
    private static final DateTimeFormatter KEY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm");

    public SQLiteBackupProduce(AgroalDataSource dataSource, S3Properties s3Properties)
    {
        this.dataSource = dataSource;
        this.s3Properties = s3Properties;
        this.s3Client = S3Helper.createClient(this.s3Properties);
    }

    @Override
    public void run()
    {
        this.backup();
    }

    @Override
    public void backup()
    {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("sqlite-backup-", ".db");
            this.dumpSqliteDatabase(tmp);
            this.uploadDump(tmp);
        }
        catch (IOException | SQLException | SdkException e) {
            logger.error("SQLite backup failed", e);
        }
        finally {
            this.deleteSilently(tmp);
        }
    }

    private void dumpSqliteDatabase(Path dest) throws SQLException
    {
        try (Connection conn = this.dataSource.getConnection(); Statement stmt = conn.createStatement())
        {
            stmt.executeUpdate("backup to " + dest.toAbsolutePath());
        }
        logger.debug("SQLite backup written to {}", dest);
    }

    private void uploadDump(Path file) throws IOException
    {
        String key = this.s3Properties.keyPrefix() + "backup-" + LocalDateTime.now(ZoneOffset.UTC).format(KEY_FMT) + ".db";

        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(this.s3Properties.bucket())
            .key(key)
            .contentType("application/octet-stream")
            .build();

        this.s3Client.putObject(request, file);
        logger.info("Backup uploaded: {} ({} bytes)", key, Files.size(file));
    }

    private void deleteSilently(Path path)
    {
        try {
            if (path == null)
                return;
            Files.deleteIfExists(path);
            logger.trace("Deleted temp file at {}", path);
        }
        catch (IOException e) {
            logger.warn("Failed to delete temp file at {}", path, e);
        }
    }

    @Override
    public void close()
    {
        this.s3Client.close();
    }
}
