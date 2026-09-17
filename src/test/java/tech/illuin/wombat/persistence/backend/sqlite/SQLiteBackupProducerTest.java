package tech.illuin.wombat.persistence.backend.sqlite;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import tech.illuin.wombat.persistence.backend.s3.S3TestPropertiesBuilder;
import tech.illuin.wombat.persistence.backend.sqlite.action.SQLiteBackupProduce;
import tech.illuin.wombat.persistence.backend.api.backup.BackupProperties;
import tech.illuin.wombat.persistence.backup.BackupTestPropertiesBuilder;

import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static tech.illuin.wombat.persistence.backend.sqlite.SQLiteTestHelper.createS3PropsBuilder;
import static tech.illuin.wombat.persistence.backup.BackupTestProperties.DEFAULT_BUCKET;

@QuarkusTest
class SQLiteBackupProducerTest
{
    private static final String KEY_PREFIX = "test-producer/";

    private static LocalStackContainer localstack;

    @Inject AgroalDataSource dataSource;

    @BeforeAll
    static void startLocalStack()
    {
        assumeTrue(isDockerAvailable(), "Docker is not available — skipping LocalStack-backed backup test");
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
    void backup_uploadsSqliteSnapshotToS3()
    {
        BackupProperties props = SQLiteTestHelper.createProps(localstack, withKeyPrefix(KEY_PREFIX));
        SQLiteBackupProduce producer = new SQLiteBackupProduce(this.dataSource, props.s3().orElseThrow());

        producer.backup();

        try (S3Client s3 = adminClient())
        {
            ListObjectsV2Response listing = s3.listObjectsV2(
                ListObjectsV2Request.builder().bucket(DEFAULT_BUCKET).prefix(KEY_PREFIX).build());

            assertEquals(1, listing.contents().size(), "expected one backup file");
            S3Object uploaded = listing.contents().getFirst();
            assertTrue(uploaded.key().startsWith(KEY_PREFIX + "backup-"));
            assertTrue(uploaded.key().endsWith(".db"));
            assertTrue(uploaded.size() > 0L, "uploaded file should not be empty");
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
