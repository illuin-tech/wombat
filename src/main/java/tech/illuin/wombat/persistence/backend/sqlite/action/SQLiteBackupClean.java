package tech.illuin.wombat.persistence.backend.sqlite.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;
import tech.illuin.wombat.persistence.backend.api.Action;
import tech.illuin.wombat.persistence.backend.s3.S3Helper;
import tech.illuin.wombat.persistence.backend.s3.S3Properties;
import tech.illuin.wombat.persistence.backend.api.backup.BackupCleaner;
import tech.illuin.wombat.persistence.backend.api.backup.BackupProperties.CleanupProperties;

import java.util.Comparator;
import java.util.List;

public class SQLiteBackupClean implements Action, BackupCleaner, AutoCloseable
{
    private final CleanupProperties properties;
    private final S3Properties s3Properties;
    private final S3Client s3Client;

    private static final Logger logger = LoggerFactory.getLogger(SQLiteBackupClean.class);

    public SQLiteBackupClean(CleanupProperties properties, S3Properties s3Properties)
    {
        this.properties = properties;
        this.s3Properties = s3Properties;
        this.s3Client = S3Helper.createClient(this.s3Properties);
    }

    @Override
    public void run()
    {
        this.clean();
    }

    @Override
    public void clean()
    {
        try {
            List<S3Object> objectsToDelete = this.listCleanableObjects();
            logger.debug("Cleanup process found {} backups eligible for removal", objectsToDelete.size());

            int deleted = 0;
            for (S3Object obj : objectsToDelete)
            {
                if (this.deleteObject(obj))
                    deleted++;
            }

            logger.info("Cleanup completed. Deleted {} older backups", deleted);
        }
        catch (SdkException e) {
            logger.error("SQLite backup cleanup failed", e);
        }
    }

    private List<S3Object> listCleanableObjects()
    {
        return this.s3Client
            .listObjectsV2(builder -> builder
                .bucket(this.s3Properties.bucket())
                .prefix(this.s3Properties.keyPrefix())
                .build()
            ).contents().stream()
            .filter(obj -> obj.key().endsWith(".db"))
            .sorted(Comparator.comparing(S3Object::lastModified).reversed())
            .skip(this.properties.retainLast())
            .toList();
    }

    private boolean deleteObject(S3Object object)
    {
        try {
            this.s3Client.deleteObject(builder -> builder
                .bucket(this.s3Properties.bucket())
                .key(object.key())
                .build());

            logger.debug("Deleted old backup: {}", object.key());
            return true;
        }
        catch (SdkException e) {
            logger.error("Failed to delete backup: {}", object.key(), e);
            return false;
        }
    }

    @Override
    public void close()
    {
        this.s3Client.close();
    }
}
