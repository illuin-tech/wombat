package tech.illuin.wombat.engine.secret;

import io.quarkus.runtime.configuration.DurationConverter;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.illuin.wombat.core.secret.CompositeSecretResolver;
import tech.illuin.wombat.core.secret.SecretMetadata;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.core.secret.SecretWriter;
import tech.illuin.wombat.engine.secret.database.crypto.SecretIntegrityException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretConfigTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void configuredResolversKeepKeystoreBeforeDirectoryAndEnvironment()
        throws Exception
    {
        Path directory = Files.createDirectory(this.temporaryDirectory.resolve("secrets"));
        Files.writeString(directory.resolve("shared"), "from-directory", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("directory-only"), "from-directory", StandardCharsets.UTF_8);

        Path keyStorePath = this.temporaryDirectory.resolve("secrets.p12");
        String password = "test-password";
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, password.toCharArray());
        SecretKey secretKey = new SecretKeySpec("from-keystore".getBytes(StandardCharsets.UTF_8), "AES");
        keyStore.setEntry("shared", new KeyStore.SecretKeyEntry(secretKey), new KeyStore.PasswordProtection(password.toCharArray()));
        try (var output = Files.newOutputStream(keyStorePath))
        {
            keyStore.store(output, password.toCharArray());
        }

        SecretProperties properties = load(Map.of(
            "wombat.secret.directory.path", directory.toString(),
            "wombat.secret.keystore.path", keyStorePath.toString(),
            "wombat.secret.keystore.password", password
        ));
        SecretConfig config = new SecretConfig();
        SecretResolver resolver = config.provideSecretResolver(List.of(
            config.provideKeystoreResolver(properties),
            config.provideDirectoryResolver(properties),
            config.provideEnvironmentResolver()
        ), properties);

        assertEquals("from-keystore", resolver.find("shared").orElseThrow());
        assertEquals("from-directory", resolver.find("directory-only").orElseThrow());
        assertTrue(resolver.find("missing-" + System.nanoTime()).isEmpty());
    }

    @Test
    void databaseResolverPrecedesFallbacksAndIsNotReadDuringConstruction() throws Exception
    {
        Path directory = Files.createDirectory(this.temporaryDirectory.resolve("db-first-secrets"));
        Files.writeString(directory.resolve("shared"), "from-directory", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("fallback-only"), "from-directory", StandardCharsets.UTF_8);
        SecretProperties properties = load(Map.of(
            "wombat.secret.directory.path", directory.toString(),
            "wombat.secret.write-mode", "DATABASE",
            /* Reads through to the database each time: the values change behind the resolver */
            "wombat.secret.cache.ttl", "0s"
        ));
        int[] reads = {0};
        Map<String, String> databaseValues = new HashMap<>(Map.of("shared", "from-database"));
        SecretResolver database = resolver(key -> {
            reads[0]++;
            return Optional.ofNullable(databaseValues.get(key));
        });

        SecretConfig config = new SecretConfig();
        SecretResolver resolver = config.provideSecretResolver(List.of(
            SecretConfig.SecretResolverCandidate.of(database),
            config.provideDirectoryResolver(properties),
            config.provideEnvironmentResolver()
        ), properties);
        assertEquals(0, reads[0]);
        assertEquals("from-database", resolver.find("shared").orElseThrow());
        assertEquals("from-directory", resolver.find("fallback-only").orElseThrow());
        databaseValues.remove("shared");
        assertEquals("from-directory", resolver.find("shared").orElseThrow());
        assertEquals(3, reads[0]);

        SecretResolver failingDatabase = resolver(key -> {
            throw new SecretIntegrityException("tampered row", new SecurityException());
        });
        SecretResolver failClosedResolver = config.provideSecretResolver(List.of(
            SecretConfig.SecretResolverCandidate.of(failingDatabase),
            config.provideDirectoryResolver(properties),
            config.provideEnvironmentResolver()
        ), properties);
        assertThrows(SecretIntegrityException.class, () -> failClosedResolver.find("shared"));

        SecretResolver unavailableDatabase = resolver(key -> {
            throw new IllegalStateException("Database or KEK unavailable");
        });
        SecretResolver unavailableResolver = config.provideSecretResolver(List.of(
            SecretConfig.SecretResolverCandidate.of(unavailableDatabase),
            config.provideDirectoryResolver(properties),
            config.provideEnvironmentResolver()
        ), properties);
        assertThrows(IllegalStateException.class, () -> unavailableResolver.find("shared"));
    }

    @Test
    void missingAndBlankOptionalPathsDisableResolvers()
    {
        SecretConfig config = new SecretConfig();
        SecretProperties missing = load(Map.of());
        SecretProperties blank = load(Map.of(
            "wombat.secret.keystore.path", " ",
            "wombat.secret.directory.path", " "
        ));

        assertFalse(config.provideKeystoreResolver(missing).isElected());
        assertFalse(config.provideDirectoryResolver(missing).isElected());
        assertFalse(config.provideKeystoreResolver(blank).isElected());
        assertFalse(config.provideDirectoryResolver(blank).isElected());
    }

    @Test
    void positiveTtlCachesTheCompositeAndInvalidatesOnWrites()
    {
        Map<String, String> values = new HashMap<>(Map.of("shared", "original"));
        SecretStore store = store(values);
        SecretConfig config = new SecretConfig();
        SecretResolver resolver = config.provideSecretResolver(List.of(SecretConfig.SecretResolverCandidate.of(store)), load(Map.of()));
        SecretWriter writer = config.provideSecretWriter(List.of(SecretConfig.SecretResolverCandidate.of(store)), resolver);

        assertInstanceOf(CachedSecretResolver.class, resolver);
        assertSame(store, assertInstanceOf(InvalidatingSecretWriter.class, writer).delegate());
        assertEquals("original", resolver.require("shared"));
        values.put("shared", "changed-outside");
        assertEquals("original", resolver.require("shared"));
        writer.putSecret("shared", "changed-through-api");
        assertEquals("changed-through-api", resolver.require("shared"));
    }

    @Test
    void zeroTtlLeavesTheCompositeAndTheWriterUnwrapped()
    {
        SecretStore store = store(new HashMap<>());
        SecretConfig config = new SecretConfig();
        SecretResolver resolver = config.provideSecretResolver(
            List.of(SecretConfig.SecretResolverCandidate.of(store)),
            load(Map.of("wombat.secret.cache.ttl", "0s"))
        );
        SecretWriter writer = config.provideSecretWriter(List.of(SecretConfig.SecretResolverCandidate.of(store)), resolver);

        assertInstanceOf(CompositeSecretResolver.class, resolver);
        assertSame(store, writer);
    }

    private static SecretProperties load(Map<String, String> values)
    {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
            .withMapping(SecretProperties.class)
            .withConverter(Duration.class, 100, new DurationConverter())
            .withSources(new PropertiesConfigSource(values, "test", 100))
            .build();
        return config.getConfigMapping(SecretProperties.class);
    }


    private static SecretResolver resolver(Function<String, Optional<String>> finder)
    {
        return new SecretResolver()
        {
            @Override
            public Optional<String> find(String key)
            {
                return finder.apply(key);
            }

            @Override
            public String require(String key)
            {
                return finder.apply(key).orElseThrow(() -> new NoSuchElementException(key));
            }
        };
    }

    /* Stands for a writable backend whose values can also change behind Wombat's back */
    private static SecretStore store(Map<String, String> values)
    {
        return new SecretStore()
        {
            @Override
            public Optional<String> find(String key)
            {
                return Optional.ofNullable(values.get(key));
            }

            @Override
            public String require(String key)
            {
                return this.find(key).orElseThrow(() -> new NoSuchElementException(key));
            }

            @Override
            public List<SecretMetadata> listSecrets()
            {
                return List.of();
            }

            @Override
            public void putSecret(String key, String value)
            {
                values.put(key, value);
            }

            @Override
            public boolean deleteSecret(String key)
            {
                return values.remove(key) != null;
            }
        };
    }
}
