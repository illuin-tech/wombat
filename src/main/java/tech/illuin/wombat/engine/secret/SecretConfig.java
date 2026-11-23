package tech.illuin.wombat.engine.secret;

import io.quarkus.arc.All;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.core.secret.SecretWriter;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@ApplicationScoped
public class SecretConfig
{
    private static final Logger logger = LoggerFactory.getLogger(SecretConfig.class);

    @Produces @Singleton
    public SecretResolver provideSecretResolver(@All List<SecretResolverCandidate> resolvers, SecretProperties properties)
    {
        SecretResolver composite = SecretResolver.composite(resolvers.stream()
            .filter(SecretResolverCandidate::isElected)
            .map(SecretResolverCandidate::resolver)
            .toList()
        );

        Duration ttl = properties.cache().ttl();
        if (!ttl.isPositive())
            return composite;

        logger.info("Secret cache enabled, ttl {}", ttl);
        return new CachedSecretResolver(composite, ttl);
    }

    @Produces @Singleton
    public SecretWriter provideSecretWriter(@WritableBackend @All List<SecretResolverCandidate> resolvers, SecretResolver resolver)
    {
        SecretWriter writer = resolvers.stream()
            .filter(SecretResolverCandidate::isElected)
            .filter(c -> c.resolver() instanceof SecretWriter)
            .map(c -> (SecretWriter) c.resolver())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No secret writer is selected"));

        if (resolver instanceof CachedSecretResolver cached)
            return new InvalidatingSecretWriter(writer, cached);
        return writer;
    }

    @Produces @Singleton @Priority(30)
    public SecretResolverCandidate provideKeystoreResolver(SecretProperties properties)
    {
        SecretProperties.Keystore keystore = properties.keystore();
        if (keystore.path().isEmpty() || keystore.path().get().isBlank())
            return SecretResolverCandidate.disabled();

        var builder = SecretResolver.keyStoreBuilder().type(keystore.type());
        keystore.password().ifPresent(builder::defaultKeyPassword);

        return SecretResolverCandidate.of(builder
            .load(Path.of(keystore.path().get()), keystore.password().orElse(null))
            .build()
        );
    }

    @Produces @Singleton @Priority(20)
    public SecretResolverCandidate provideDirectoryResolver(SecretProperties properties)
    {
        SecretProperties.Directory directory = properties.directory();
        if (directory.path().isEmpty() || directory.path().get().isBlank())
            return SecretResolverCandidate.disabled();
        return SecretResolverCandidate.of(SecretResolver.directory(Path.of(directory.path().get())));
    }

    @Produces @Singleton @Priority(10)
    public SecretResolverCandidate provideEnvironmentResolver()
    {
        return new SecretResolverCandidate(SecretResolver.environment());
    }

    public record SecretResolverCandidate(
        SecretResolver resolver
    ) {
        public boolean isElected()
        {
            return this.resolver != null;
        }

        public static SecretResolverCandidate of(SecretResolver resolver)
        {
            return new SecretResolverCandidate(resolver);
        }

        public static SecretResolverCandidate disabled()
        {
            return new SecretResolverCandidate(null);
        }
    }
}
