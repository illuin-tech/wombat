package tech.illuin.wombat.engine.secret.database.crypto;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import tech.illuin.wombat.engine.secret.SecretStore;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.engine.secret.database.DatabaseSecretStore;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(value = RestoredSecretStartupTest.RestoredDatabaseResource.class, restrictToAnnotatedClass = true)
public class RestoredSecretStartupTest
{
    private static final String SECRET_KEY = "WOMBAT_RESTORED_TEST_SECRET";
    private static final String SECRET_VALUE = "restored-test-value";
    private static final String TEST_KEK = Base64.getEncoder().encodeToString(
        "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
    );

    @Inject SecretResolver resolver;
    @Inject DatabaseSecretStore store;

    @Test
    void restoredDatabaseSuppliesRequiredSecretDuringStartup()
    {
        assertEquals(SECRET_VALUE, this.store.require(SECRET_KEY));
        assertEquals(SECRET_VALUE, this.resolver.require(SECRET_KEY));
    }

    public static class RestoredDatabaseResource implements QuarkusTestResourceLifecycleManager
    {
        private LocalStackContainer localstack;
        private Path directory;
        private Path backup;
        private Path target;

        @Override
        public Map<String, String> start()
        {
            try
            {
                this.directory = Files.createTempDirectory(Path.of("target"), "secret-restore-");
                this.backup = this.directory.resolve("backup.db");
                this.target = this.directory.resolve("restored.db").toAbsolutePath();
                createBackup(this.backup);

                this.localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4"))
                    .withServices(LocalStackContainer.Service.S3);
                this.localstack.start();
                try (S3Client s3 = s3Client())
                {
                    s3.createBucket(CreateBucketRequest.builder().bucket("secret-restore-test").build());
                    s3.putObject(
                        PutObjectRequest.builder().bucket("secret-restore-test").key("restored/backup-2026-01-01.db").build(),
                        RequestBody.fromFile(this.backup)
                    );
                }

                return Map.ofEntries(
                    Map.entry("quarkus.datasource.jdbc.url", "jdbc:sqlite:" + this.target),
                    Map.entry("quarkus.flyway.clean-at-start", "false"),
                    Map.entry("quarkus.flyway.migrate-at-start", "false"),
                    Map.entry("quarkus.scheduler.enabled", "true"),
                    Map.entry("monitor.environments-file", "src/test/resources/monitored-restored-secret.yaml"),
                    Map.entry("backup.enabled", "true"),
                    Map.entry("backup.restore-on-startup", "true"),
                    Map.entry("backup.cleanup.enabled", "false"),
                    Map.entry("backup.s3.endpoint", this.localstack.getEndpoint().toString()),
                    Map.entry("backup.s3.bucket", "secret-restore-test"),
                    Map.entry("backup.s3.region", this.localstack.getRegion()),
                    Map.entry("backup.s3.key-prefix", "restored/"),
                    Map.entry("backup.s3.access-key", this.localstack.getAccessKey()),
                    Map.entry("backup.s3.secret-key", this.localstack.getSecretKey()),
                    Map.entry("wombat.secret.write-mode", "DATABASE"),
                    Map.entry("wombat.secret.database.active-kek-id", "test"),
                    Map.entry("wombat.secret.database.kek.test", TEST_KEK)
                );
            }
            catch (Exception e)
            {
                stop();
                throw new IllegalStateException("Could not prepare restored secret startup test", e);
            }
        }

        @Override
        public void stop()
        {
            if (this.localstack != null)
                this.localstack.stop();
            try
            {
                if (this.target != null)
                {
                    Files.deleteIfExists(Path.of(this.target + "-wal"));
                    Files.deleteIfExists(Path.of(this.target + "-shm"));
                    Files.deleteIfExists(this.target);
                }
                if (this.backup != null)
                    Files.deleteIfExists(this.backup);
                if (this.directory != null)
                    Files.deleteIfExists(this.directory);
            }
            catch (Exception e)
            {
                throw new IllegalStateException("Could not clean up restored secret startup test", e);
            }
        }

        private S3Client s3Client()
        {
            return S3Client.builder()
                .endpointOverride(URI.create(this.localstack.getEndpoint().toString()))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(this.localstack.getAccessKey(), this.localstack.getSecretKey())))
                .region(Region.of(this.localstack.getRegion()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        }

        private static void createBackup(Path path) throws Exception
        {
            String jdbcUrl = "jdbc:sqlite:" + path.toAbsolutePath();
            Flyway.configure().dataSource(jdbcUrl, null, null).locations("classpath:db/migration/sqlite").load().migrate();
            KeyEncryptionKeyProvider provider = new KeyEncryptionKeyProvider()
            {
                @Override
                public String activeKeyId()
                {
                    return "test";
                }

                @Override
                public byte[] key(String id)
                {
                    return Base64.getDecoder().decode(TEST_KEK);
                }
            };
            SecretCryptography.EncryptedSecret encrypted = new SecretCryptography(provider).encrypt(SECRET_KEY, SECRET_VALUE);
            try (Connection connection = DriverManager.getConnection(jdbcUrl);
                 PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO secrets (secret_key, payload_ciphertext, payload_nonce, wrapped_dek, wrapping_nonce, kek_id, created_at, updated_at, revision) "
                         + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)"))
            {
                statement.setString(1, SECRET_KEY);
                statement.setBytes(2, encrypted.ciphertext());
                statement.setBytes(3, encrypted.payloadNonce());
                statement.setBytes(4, encrypted.wrappedDek());
                statement.setBytes(5, encrypted.wrappingNonce());
                statement.setString(6, encrypted.kekId());
                statement.setLong(7, Instant.now().toEpochMilli());
                statement.setLong(8, Instant.now().toEpochMilli());
                statement.executeUpdate();
            }
        }
    }
}