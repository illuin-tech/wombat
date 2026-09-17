package tech.illuin.wombat.persistence.backend.sqlite;

import org.testcontainers.containers.localstack.LocalStackContainer;
import tech.illuin.wombat.persistence.backend.s3.S3Properties;
import tech.illuin.wombat.persistence.backend.s3.S3TestProperties;
import tech.illuin.wombat.persistence.backend.s3.S3TestPropertiesBuilder;
import tech.illuin.wombat.persistence.backend.api.backup.BackupProperties;
import tech.illuin.wombat.persistence.backup.BackupTestProperties;
import tech.illuin.wombat.persistence.backup.BackupTestPropertiesBuilder;

import java.util.Optional;
import java.util.function.Consumer;

public final class SQLiteTestHelper
{
    private SQLiteTestHelper() {}

    public static BackupProperties createProps(Consumer<BackupTestPropertiesBuilder> customizer)
    {
        BackupTestPropertiesBuilder builder = BackupTestProperties.ofDefault();
        customizer.accept(builder);
        return builder.build();
    }

    public static BackupProperties createProps(LocalStackContainer localstack, Consumer<BackupTestPropertiesBuilder> customizer)
    {
        BackupTestPropertiesBuilder builder = BackupTestProperties.ofDefault();
        builder.s3(Optional.of(createS3PropsBuilder(builder.s3())
            .endpoint(localstack.getEndpoint().toString())
            .accessKey(localstack.getAccessKey())
            .secretKey(localstack.getSecretKey())
            .build()
        ));
        customizer.accept(builder);
        return builder.build();
    }

    public static S3TestPropertiesBuilder createS3PropsBuilder(Optional<S3Properties> properties)
    {
        return properties.map(s3 -> S3TestPropertiesBuilder.builder((S3TestProperties) s3)).orElse(S3TestPropertiesBuilder.builder());
    }
}
