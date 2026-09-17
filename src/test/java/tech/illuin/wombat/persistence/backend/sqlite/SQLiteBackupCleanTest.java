package tech.illuin.wombat.persistence.backend.sqlite;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterAll;
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
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import tech.illuin.wombat.persistence.backend.s3.S3TestPropertiesBuilder;
import tech.illuin.wombat.persistence.backend.sqlite.action.SQLiteBackupClean;
import tech.illuin.wombat.persistence.backend.api.backup.BackupProperties;
import tech.illuin.wombat.persistence.backup.BackupTestPropertiesBuilder;
import tech.illuin.wombat.persistence.backup.CleanupTestProperties;
import tech.illuin.wombat.persistence.backup.CleanupTestPropertiesBuilder;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static tech.illuin.wombat.persistence.backend.sqlite.SQLiteTestHelper.createS3PropsBuilder;
import static tech.illuin.wombat.persistence.backup.BackupTestProperties.DEFAULT_BUCKET;

@QuarkusTest
class SQLiteBackupCleanTest
{
    private static final String CLEANUP_KEY_PREFIX = "test-cleanup/";
    private static final String WITHIN_LIMIT_KEY_PREFIX = "test-cleanup-within-limit/";

    private static LocalStackContainer localstack;

    @BeforeAll
    static void startLocalStack()
    {
        assumeTrue(isDockerAvailable(), "Docker is not available — skipping LocalStack-backed cleanup test");
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
        if (localstack != null)
            localstack.stop();
    }

    @Test
    void clean_deletesOldestBackups_beyondRetainLimit() throws Exception
    {
        int retainLast = 2;
        List<String> keys = List.of(
            CLEANUP_KEY_PREFIX + "backup-1.db",
            CLEANUP_KEY_PREFIX + "backup-2.db",
            CLEANUP_KEY_PREFIX + "backup-3.db",
            CLEANUP_KEY_PREFIX + "backup-4.db"
        );

        try (S3Client s3 = adminClient())
        {
            for (String key : keys)
            {
                s3.putObject(PutObjectRequest.builder().bucket(DEFAULT_BUCKET).key(key).build(), RequestBody.fromBytes("backup".getBytes()));
                Thread.sleep(1100); // ensure distinct lastModified timestamps, as S3/LocalStack timestamps have second granularity
            }
        }

        BackupProperties props = SQLiteTestHelper.createProps(localstack, withKeyPrefixAndRetainLast(CLEANUP_KEY_PREFIX, retainLast));
        SQLiteBackupClean cleaner = new SQLiteBackupClean(props.cleanup(), props.s3().orElseThrow());

        cleaner.clean();

        try (S3Client s3 = adminClient())
        {
            ListObjectsV2Response listing = s3.listObjectsV2(
                ListObjectsV2Request.builder().bucket(DEFAULT_BUCKET).prefix(CLEANUP_KEY_PREFIX).build());

            assertEquals(retainLast, listing.contents().size(), "only the most recent backups should be retained");
            List<String> remainingKeys = listing.contents().stream().map(S3Object::key).toList();
            assertTrue(remainingKeys.contains(keys.get(2)), "the second-to-last backup should have been retained");
            assertTrue(remainingKeys.contains(keys.get(3)), "the most recent backup should have been retained");
        }
    }

    @Test
    void clean_keepsAllBackups_whenCountIsWithinRetainLimit()
    {
        List<String> keys = List.of(
            WITHIN_LIMIT_KEY_PREFIX + "backup-1.db",
            WITHIN_LIMIT_KEY_PREFIX + "backup-2.db"
        );

        try (S3Client s3 = adminClient())
        {
            for (String key : keys)
                s3.putObject(PutObjectRequest.builder().bucket(DEFAULT_BUCKET).key(key).build(), RequestBody.fromBytes("backup".getBytes()));
        }

        BackupProperties props = SQLiteTestHelper.createProps(localstack, withKeyPrefixAndRetainLast(WITHIN_LIMIT_KEY_PREFIX, 10));
        SQLiteBackupClean cleaner = new SQLiteBackupClean(props.cleanup(), props.s3().orElseThrow());

        cleaner.clean();

        try (S3Client s3 = adminClient())
        {
            ListObjectsV2Response listing = s3.listObjectsV2(
                ListObjectsV2Request.builder().bucket(DEFAULT_BUCKET).prefix(WITHIN_LIMIT_KEY_PREFIX).build());

            assertEquals(keys.size(), listing.contents().size(), "no backup should be deleted when the retain limit is not exceeded");
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

    public static Consumer<BackupTestPropertiesBuilder> withKeyPrefixAndRetainLast(String keyPrefix, int retainLast)
    {
        return b -> b
            .s3(Optional.of(createS3PropsBuilder(b.s3()).keyPrefix(keyPrefix).build()))
            .cleanup(CleanupTestPropertiesBuilder.builder((CleanupTestProperties) b.cleanup()).retainLast(retainLast).build());
    }
}
