package tech.illuin.wombat.persistence.backend.s3;

import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder
public record S3TestProperties(
    String endpoint,
    String bucket,
    String region,
    String keyPrefix,
    String accessKey,
    String secretKey
) implements S3Properties {}
