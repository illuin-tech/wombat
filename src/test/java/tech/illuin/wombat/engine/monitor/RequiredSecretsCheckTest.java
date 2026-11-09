package tech.illuin.wombat.engine.monitor;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.secret.CompositeSecretResolver;
import tech.illuin.wombat.core.secret.EnvironmentSecretResolver;
import tech.illuin.wombat.core.secret.KeyStoreSecretResolver;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.core.source.Period;
import tech.illuin.wombat.engine.monitor.MonitoredEnvironments;
import tech.illuin.wombat.engine.monitor.RequiredSecretsCheck;
import tech.illuin.wombat.engine.secret.SecretProperties;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusAsset;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusProfile;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticProfile;
import tech.illuin.wombat.module.llm_static.scenario.ConstantScenario;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.illuin.wombat.core.activity.commons.Country.FRANCE;

/**
 * Exercised as a plain object rather than through a Quarkus boot: the failure being asserted is the application
 * refusing to start, which a {@code @QuarkusTest} cannot observe from the inside.
 */
class RequiredSecretsCheckTest
{
    @Test
    void startsWhenEveryDeclaredVariableIsSet()
    {
        RequiredSecretsCheck check = check(
            Map.of("PROM_PASSWORD", "s3cret"),
            prometheusAsset("p", "user", "PROM_PASSWORD"));

        assertDoesNotThrow(() -> check.onStartEarly(new StartupEvent()));
    }

    @Test
    void startsWhenKeyStoreResolvesSecret() throws Exception
    {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        var secretKey = new SecretKeySpec("s3cret".getBytes(StandardCharsets.UTF_8), "AES");
        ks.setEntry("PROM_PASSWORD", new KeyStore.SecretKeyEntry(secretKey), new KeyStore.PasswordProtection("pass".toCharArray()));

        KeyStoreSecretResolver ksResolver = KeyStoreSecretResolver.builder()
            .keyStore(ks)
            .defaultKeyPassword("pass")
            .build();

        CompositeSecretResolver composite = new CompositeSecretResolver(ksResolver, new EnvironmentSecretResolver(key -> null));

        MonitoredEnvironments environments = new MonitoredEnvironments(Map.of(
            "env", new Environment("env", List.of(prometheusAsset("p", "user", "PROM_PASSWORD")))
        ));
        RequiredSecretsCheck check = new RequiredSecretsCheck(environments, composite, secretProperties(false));

        assertDoesNotThrow(() -> check.onStartEarly(new StartupEvent()));
    }

    @Test
    void startsWhenNoAssetNeedsACredential()
    {
        RequiredSecretsCheck check = check(Map.of(), prometheusAsset("p", null, null), staticAsset());

        assertDoesNotThrow(() -> check.onStartEarly(new StartupEvent()));
    }

    @Test
    void refusesToStartWhenADeclaredVariableIsMissing()
    {
        RequiredSecretsCheck check = check(Map.of(), prometheusAsset("p", "user", "PROM_PASSWORD"));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> check.onStartEarly(new StartupEvent()));

        assertTrue(e.getMessage().contains("PROM_PASSWORD"), e.getMessage());
    }

    @Test
    void refusesToStartWhenADeclaredVariableIsExportedEmpty()
    {
        RequiredSecretsCheck check = check(Map.of("PROM_PASSWORD", ""), prometheusAsset("p", "user", "PROM_PASSWORD"));

        assertThrows(IllegalStateException.class, () -> check.onStartEarly(new StartupEvent()));
    }

    @Test
    void reportsEveryMissingVariableAtOnce()
    {
        // One restart per missing credential is a slow way to fix a deployment, so the check collects them all.
        RequiredSecretsCheck check = check(
            Map.of("SECOND_PASSWORD", "s3cret"),
            prometheusAsset("first", "user", "FIRST_PASSWORD"),
            prometheusAsset("second", "user", "SECOND_PASSWORD"),
            prometheusAsset("third", "user", "THIRD_PASSWORD"));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> check.onStartEarly(new StartupEvent()));

        assertTrue(e.getMessage().contains("FIRST_PASSWORD"), e.getMessage());
        assertTrue(e.getMessage().contains("THIRD_PASSWORD"), e.getMessage());
    }

    @Test
    void checksDatabaseSecretsAfterPersistenceSetup()
    {
        AtomicInteger lookups = new AtomicInteger();
        SecretResolver resolver = resolver(key -> {
            lookups.incrementAndGet();
            return Optional.empty();
        });
        RequiredSecretsCheck check = check(
            resolver,
            true,
            prometheusAsset("p", "user", "PROM_PASSWORD")
        );

        assertDoesNotThrow(() -> check.onStartEarly(new StartupEvent()));
        assertEquals(0, lookups.get());

        IllegalStateException e = assertThrows(
            IllegalStateException.class,
            () -> check.onStartAfterPersistenceSetup(new StartupEvent())
        );
        assertTrue(e.getMessage().contains("PROM_PASSWORD"), e.getMessage());
        assertEquals(1, lookups.get());
        assertTrue(RequiredSecretsCheck.STARTUP_PRIORITY_SECRETS_DATABASE > 1000);
        assertTrue(RequiredSecretsCheck.STARTUP_PRIORITY_SECRETS_DATABASE < 2000);
    }

    private static RequiredSecretsCheck check(Map<String, String> environment, Asset... assets)
    {
        return check(new EnvironmentSecretResolver(environment::get), false, assets);
    }

    private static RequiredSecretsCheck check(SecretResolver secrets, boolean databaseEnabled, Asset... assets)
    {
        MonitoredEnvironments environments = new MonitoredEnvironments(
            Map.of("env", new Environment("env", List.of(assets))));
        SecretProperties properties = secretProperties(databaseEnabled);
        return new RequiredSecretsCheck(environments, secrets, properties);
    }

    private static SecretProperties secretProperties(boolean databaseEnabled)
    {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
            .withMapping(SecretProperties.class)
            .withSources(new PropertiesConfigSource(Map.of(
                "wombat.secret.write-mode", databaseEnabled ? "DATABASE" : "DISABLED"
            ), "test", 100))
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

    private static LLMPrometheusAsset prometheusAsset(String id, String username, String passwordEnv)
    {
        return new LLMPrometheusAsset(
            AssetIdentity.of(id, "env", id),
            "http://prometheus", null, username, passwordEnv, Period.ZERO,
            new LLMPrometheusProfile(LLMProvider.mistralai, "m", FRANCE, new LLMPrometheusProfile.DynamicProfile("q"))
        );
    }

    private static LLMStaticAsset staticAsset()
    {
        return new LLMStaticAsset(
            AssetIdentity.of("s", "env", "S"),
            new LLMStaticProfile(LLMProvider.mistralai, "m", FRANCE, List.of(
                new ConstantScenario(500, 1000)
            ))
        );
    }
}
