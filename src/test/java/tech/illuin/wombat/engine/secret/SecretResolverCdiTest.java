package tech.illuin.wombat.engine.secret;

import io.quarkus.arc.All;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.secret.CompositeSecretResolver;
import tech.illuin.wombat.core.secret.EnvironmentSecretResolver;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.core.secret.SecretWriter;
import tech.illuin.wombat.engine.secret.SecretProperties.WriteMode;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = SecretResolverCdiTest.ResolverSources.class, restrictToAnnotatedClass = true)
class SecretResolverCdiTest
{
    @Inject SecretResolver resolver;
    @Inject @All
    List<SecretConfig.SecretResolverCandidate> candidates;
    @Inject @WritableBackend @All
    List<SecretConfig.SecretResolverCandidate> writableBackends;
    @Inject Instance<SecretWriter> writer;

    @Test
    void composesElectedCdiSourcesInPriorityOrder()
    {
        List<SecretResolver> sources = this.candidates.stream()
            .filter(SecretConfig.SecretResolverCandidate::isElected)
            .map(SecretConfig.SecretResolverCandidate::resolver)
            .toList();

        assertEquals(3, sources.size());
        assertEquals(sources, assertInstanceOf(CompositeSecretResolver.class, this.resolver).resolvers());
        assertEquals("from-keystore", sources.getFirst().find("shared").orElseThrow());
        assertEquals("from-directory", sources.get(1).find("shared").orElseThrow());
        assertInstanceOf(EnvironmentSecretResolver.class, sources.getLast());
        assertEquals("from-keystore", this.resolver.find("shared").orElseThrow());
        assertEquals("from-directory", this.resolver.find("directory-only").orElseThrow());
    }

    @Test
    void disabledWritesStartWithoutDatabaseKekConfiguration()
    {
        assertEquals(2, this.writableBackends.size());
        assertTrue(this.writableBackends.stream().noneMatch(SecretConfig.SecretResolverCandidate::isElected));
        assertThrows(IllegalStateException.class, () -> this.writer.get());
    }

    public static class ResolverSources implements QuarkusTestResourceLifecycleManager
    {
        private static final String PASSWORD = "resolver-source-password";
        private Path root;

        @Override
        public Map<String, String> start()
        {
            try {
                this.root = Files.createTempDirectory(Path.of("target"), "secret-resolver-cdi-");
                Path directory = Files.createDirectory(this.root.resolve("secrets"));
                Files.writeString(directory.resolve("shared"), "from-directory");
                Files.writeString(directory.resolve("directory-only"), "from-directory");

                Path keyStorePath = this.root.resolve("secrets.p12");
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(null, PASSWORD.toCharArray());
                SecretKey secretKey = new SecretKeySpec("from-keystore".getBytes(StandardCharsets.UTF_8), "AES");
                keyStore.setEntry(
                    "shared",
                    new KeyStore.SecretKeyEntry(secretKey),
                    new KeyStore.PasswordProtection(PASSWORD.toCharArray())
                );

                try (var output = Files.newOutputStream(keyStorePath)) {
                    keyStore.store(output, PASSWORD.toCharArray());
                }

                return Map.of(
                    "wombat.secret.write-mode", WriteMode.disabled.name(),
                    "wombat.secret.keystore.path", keyStorePath.toString(),
                    "wombat.secret.keystore.password", PASSWORD,
                    "wombat.secret.directory.path", directory.toString()
                );
            }
            catch (Exception e) {
                throw new IllegalStateException("Could not prepare resolver CDI test sources", e);
            }
        }

        @Override
        public void stop()
        {
            if (this.root == null)
                return;
            try {
                Files.deleteIfExists(this.root.resolve("secrets.p12"));
                Files.deleteIfExists(this.root.resolve("secrets/shared"));
                Files.deleteIfExists(this.root.resolve("secrets/directory-only"));
                Files.deleteIfExists(this.root.resolve("secrets"));
                Files.deleteIfExists(this.root);
            }
            catch (Exception e) {
                throw new IllegalStateException("Could not clean resolver CDI test sources", e);
            }
        }
    }
}