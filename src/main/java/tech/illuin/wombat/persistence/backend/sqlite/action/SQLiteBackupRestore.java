package tech.illuin.wombat.persistence.backend.sqlite.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import tech.illuin.wombat.persistence.backend.api.Action;
import tech.illuin.wombat.persistence.backend.s3.S3Helper;
import tech.illuin.wombat.persistence.backend.s3.S3Properties;
import tech.illuin.wombat.persistence.backend.api.backup.BackupRestorer;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Optional;

public class SQLiteBackupRestore implements BackupRestorer, Action
{
    private final Path dbPath;
    private final S3Properties s3Properties;
    private final S3Client s3Client;

    private static final Logger logger = LoggerFactory.getLogger(SQLiteBackupRestore.class);

    public SQLiteBackupRestore(Path dbPath, S3Properties s3Properties)
    {
        this.dbPath = dbPath;
        this.s3Properties = s3Properties;
        this.s3Client = S3Helper.createClient(this.s3Properties);
    }

    @Override
    public void run()
    {
        try {
            if (this.isDatabaseMissingOrEmpty())
                logger.info("Database file already exists, skipping restoration");
            else {
                logger.info("Attempting database restoration from remote backup");
                this.restore();
            }
        }
        catch (RestoreException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private boolean isDatabaseMissingOrEmpty()
    {
        if (!Files.exists(this.dbPath))
            return false;
        try {
            return Files.size(this.dbPath) > 0;
        }
        catch (IOException e) {
            logger.warn("Failed to check database file size at {}, assuming it already has data", this.dbPath, e);
            return true;
        }
    }

    @Override
    public boolean restore() throws RestoreException
    {
        Path tmp = null;
        try {
            Optional<S3Object> latest = this.findLatestBackup();
            if (latest.isEmpty())
            {
                logger.info("No SQLite backup found in S3 under prefix {}, skipping restore", this.s3Properties.keyPrefix());
                return false;
            }

            String key = latest.get().key();
            tmp = Files.createTempFile("sqlite-restore-", ".db");
            Files.deleteIfExists(tmp);
            this.download(key, tmp);

            this.overwriteInPlace(tmp, this.dbPath);

            logger.info("Restored SQLite database from S3 backup {} to {}", key, this.dbPath);
            return true;
        }
        catch (IOException | SdkException e) {
            throw new RestoreException("SQLite database restore failed: " + e.getMessage(), e);
        }
        finally {
            this.deleteSilently(tmp);
        }
    }

    private Optional<S3Object> findLatestBackup()
    {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
            .bucket(this.s3Properties.bucket())
            .prefix(this.s3Properties.keyPrefix())
            .build();

        ListObjectsV2Response response = this.s3Client.listObjectsV2(request);
        return response.contents().stream()
            .filter(o -> o.key().endsWith(".db"))
            .max(Comparator.comparing(S3Object::key));
    }

    /**
     * We currently perform an in-place rewrite since the inode may be read by SQLite (although not in use at this point).
     *
     * @param source
     * @param dest
     * @throws IOException
     */
    private void overwriteInPlace(Path source, Path dest) throws IOException
    {
        Path parent = dest.getParent();
        if (parent != null)
            Files.createDirectories(parent);

        try (
            FileChannel in = FileChannel.open(source, StandardOpenOption.READ);
            FileChannel out = FileChannel.open(dest, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        ) {
            long size = in.size();
            long transferred = 0;
            while (transferred < size)
                transferred += in.transferTo(transferred, size - transferred, out);
        }
    }

    private void download(String key, Path dest) throws IOException
    {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(this.s3Properties.bucket())
            .key(key)
            .build();

        this.s3Client.getObject(request, dest);
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
}
