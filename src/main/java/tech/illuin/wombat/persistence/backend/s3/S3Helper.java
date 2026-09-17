package tech.illuin.wombat.persistence.backend.s3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

public final class S3Helper
{
    private static final Logger logger = LoggerFactory.getLogger(S3Helper.class);

    private S3Helper() {}

    /**
     * Creates an Amazon S3 client using the provided S3 properties.
     * Configures the client with endpoint, region, and optional credentials.
     *
     * @param props
     * @return
     */
    public static S3Client createClient(S3Properties props)
    {
        logger.info("SQLite S3 backup enabled — endpoint={} bucket={}", props.endpoint(), props.bucket());
        S3ClientBuilder builder = S3Client.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .endpointOverride(URI.create(props.endpoint()))
            .region(Region.of(props.region()))
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build()
            );

        if (!props.accessKey().isBlank() && !props.secretKey().isBlank())
        {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKey(), props.secretKey()))
            );
        }

        return builder.build();
    }
}
