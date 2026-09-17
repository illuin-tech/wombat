package tech.illuin.wombat.persistence.backend.sqlite;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tech.illuin.wombat.persistence.backend.sqlite.action.SQLiteBackupProduce;
import tech.illuin.wombat.persistence.backend.sqlite.action.SQLiteBackupRestore;
import tech.illuin.wombat.persistence.backend.api.backup.BackupProperties;
import tech.illuin.wombat.persistence.backup.BackupTestPropertiesBuilder;

import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static tech.illuin.wombat.persistence.backend.sqlite.SQLiteTestHelper.createS3PropsBuilder;
import static tech.illuin.wombat.persistence.backup.BackupTestProperties.DEFAULT_BUCKET;

@QuarkusTest
class SQLiteBackupRestorerTest
{
    private static final String RESTORE_KEY_PREFIX = "test-restore/";
    private static final String EMPTY_KEY_PREFIX = "test-empty/";
    private static final String ORDERING_KEY_PREFIX = "test-ordering/";
    private static final String IN_PLACE_KEY_PREFIX = "test-in-place/";

    private static LocalStackContainer localstack;

    @Inject AgroalDataSource dataSource;

    @BeforeAll
    static void startLocalStack()
    {
        assumeTrue(isDockerAvailable(), "Docker is not available — skipping LocalStack-backed restore test");
        localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4"))
            .withServices(LocalStackContainer.Service.S3);
        localstack.start();

        try (S3Client s3 = adminClient())
        {
            s3.createBucket(CreateBucketRequest.builder().bucket(DEFAULT_BUCKET).build());
        }
    }

    @AfterAll
    static void stopLocalStack()
    {
        if (localstack != null) localstack.stop();
    }

    @Test
    void restore_restoresLatestBackupFromS3() throws Exception
    {
        BackupProperties props = SQLiteTestHelper.createProps(localstack, withKeyPrefix(RESTORE_KEY_PREFIX));
        SQLiteBackupProduce producer = new SQLiteBackupProduce(this.dataSource, props.s3().orElseThrow());
        producer.backup();

        Path restoreTarget = Files.createTempFile("sqlite-restore-target-", ".db");
        Files.deleteIfExists(restoreTarget);
        SQLiteBackupRestore restorer = new SQLiteBackupRestore(restoreTarget, props.s3().orElseThrow());

        try {
            boolean restored = Assertions.assertDoesNotThrow(restorer::restore);

            assertTrue(restored, "restore should report success when a backup is available");
            assertTrue(Files.exists(restoreTarget), "restored database file should exist");
            assertTrue(Files.size(restoreTarget) > 0L, "restored database file should not be empty");
        }
        finally {
            Files.deleteIfExists(restoreTarget);
        }
    }

    @Test
    void restore_returnsFalseWhenNoBackupIsAvailable() throws Exception
    {
        BackupProperties props = SQLiteTestHelper.createProps(localstack, withKeyPrefix(EMPTY_KEY_PREFIX));
        Path restoreTarget = Files.createTempFile("sqlite-restore-target-", ".db");
        Files.deleteIfExists(restoreTarget);
        SQLiteBackupRestore restorer = new SQLiteBackupRestore(restoreTarget, props.s3().orElseThrow());

        try {
            boolean restored = Assertions.assertDoesNotThrow(restorer::restore);

            assertFalse(restored, "restore should report failure when no backup exists");
            assertFalse(Files.exists(restoreTarget), "no database file should be created when restore fails");
        }
        finally {
            Files.deleteIfExists(restoreTarget);
        }
    }

    @Test
    void restore_picksMostRecentBackupWhenMultipleExist() throws Exception
    {
        BackupProperties props = SQLiteTestHelper.createProps(localstack, withKeyPrefix(ORDERING_KEY_PREFIX));
        byte[] oldContent = "old-backup".getBytes();
        byte[] newContent = "new-backup-content".getBytes();

        try (S3Client s3 = adminClient())
        {
            s3.putObject(
                PutObjectRequest.builder().bucket(DEFAULT_BUCKET).key(ORDERING_KEY_PREFIX + "backup-2020-01-01-00-00.db").build(),
                RequestBody.fromBytes(oldContent));
            s3.putObject(
                PutObjectRequest.builder().bucket(DEFAULT_BUCKET).key(ORDERING_KEY_PREFIX + "backup-2030-01-01-00-00.db").build(),
                RequestBody.fromBytes(newContent));
        }

        Path restoreTarget = Files.createTempFile("sqlite-restore-target-", ".db");
        Files.deleteIfExists(restoreTarget);
        SQLiteBackupRestore restorer = new SQLiteBackupRestore(restoreTarget, props.s3().orElseThrow());

        try {
            boolean restored = Assertions.assertDoesNotThrow(restorer::restore);

            assertTrue(restored);
            assertArrayEquals(newContent, Files.readAllBytes(restoreTarget), "the most recent backup should have been restored");
        }
        finally {
            Files.deleteIfExists(restoreTarget);
        }
    }

    @Test
    void restore_overwritesExistingFileInPlace_preservingFileIdentity() throws Exception
    {
        BackupProperties props = SQLiteTestHelper.createProps(localstack, withKeyPrefix(IN_PLACE_KEY_PREFIX));
        byte[] backupContent = "backup-content-for-in-place-restore".getBytes();

        try (S3Client s3 = adminClient())
        {
            s3.putObject(
                PutObjectRequest.builder().bucket(DEFAULT_BUCKET).key(IN_PLACE_KEY_PREFIX + "backup-2020-01-01-00-00.db").build(),
                RequestBody.fromBytes(backupContent));
        }

        Path restoreTarget = Files.createTempFile("sqlite-restore-target-", ".db");
        Files.writeString(restoreTarget, "pre-existing-placeholder-content");
        SQLiteBackupRestore restorer = new SQLiteBackupRestore(restoreTarget, props.s3().orElseThrow());

        try {
            /* Keep a file descriptor open on the destination path, mimicking a datasource/connection
             * that already opened the file before restoration runs; if restore() ever replaces the
             * file via a rename instead of writing in place, the underlying inode would change even
             * though this handle stays open on the old one. */
            try (FileChannel heldOpen = FileChannel.open(restoreTarget, StandardOpenOption.READ))
            {
                Object inodeBefore = Files.getAttribute(restoreTarget, "unix:ino");

                boolean restored = Assertions.assertDoesNotThrow(restorer::restore);

                assertTrue(restored, "restore should report success when a backup is available");
                Object inodeAfter = Files.getAttribute(restoreTarget, "unix:ino");
                assertTrue(inodeBefore.equals(inodeAfter), "restore should overwrite the file in place instead of replacing it, preserving its identity for already-open handles");
                assertArrayEquals(backupContent, Files.readAllBytes(restoreTarget), "restored file should contain the backup content");
            }
        }
        finally {
            Files.deleteIfExists(restoreTarget);
        }
    }

    private static boolean isDockerAvailable()
    {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        }
        catch (Throwable t) {
            return false;
        }
    }

    private static S3Client adminClient()
    {
        return S3Client.builder()
            .endpointOverride(URI.create(localstack.getEndpoint().toString()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
            .region(Region.of(localstack.getRegion()))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    }

    private static Consumer<BackupTestPropertiesBuilder> withKeyPrefix(String keyPrefix)
    {
        return b -> b.s3(Optional.of(createS3PropsBuilder(b.s3()).keyPrefix(keyPrefix).build()));
    }
}
