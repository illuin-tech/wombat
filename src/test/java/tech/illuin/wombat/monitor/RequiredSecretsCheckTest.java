package tech.illuin.wombat.monitor;

import io.quarkus.runtime.StartupEvent;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.secret.CompositeSecretResolver;
import tech.illuin.wombat.core.secret.EnvironmentSecretResolver;
import tech.illuin.wombat.core.secret.KeyStoreSecretResolver;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusAsset;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusProfile;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticProfile;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertDoesNotThrow(() -> check.onStart(new StartupEvent()));
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
        RequiredSecretsCheck check = new RequiredSecretsCheck(environments, composite);

        assertDoesNotThrow(() -> check.onStart(new StartupEvent()));
    }

    @Test
    void startsWhenNoAssetNeedsACredential()
    {
        RequiredSecretsCheck check = check(Map.of(), prometheusAsset("p", null, null), staticAsset());

        assertDoesNotThrow(() -> check.onStart(new StartupEvent()));
    }

    @Test
    void refusesToStartWhenADeclaredVariableIsMissing()
    {
        RequiredSecretsCheck check = check(Map.of(), prometheusAsset("p", "user", "PROM_PASSWORD"));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> check.onStart(new StartupEvent()));

        assertTrue(e.getMessage().contains("PROM_PASSWORD"), e.getMessage());
    }

    @Test
    void refusesToStartWhenADeclaredVariableIsExportedEmpty()
    {
        RequiredSecretsCheck check = check(Map.of("PROM_PASSWORD", ""), prometheusAsset("p", "user", "PROM_PASSWORD"));

        assertThrows(IllegalStateException.class, () -> check.onStart(new StartupEvent()));
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

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> check.onStart(new StartupEvent()));

        assertTrue(e.getMessage().contains("FIRST_PASSWORD"), e.getMessage());
        assertTrue(e.getMessage().contains("THIRD_PASSWORD"), e.getMessage());
    }

    private static RequiredSecretsCheck check(Map<String, String> environment, Asset... assets)
    {
        MonitoredEnvironments environments = new MonitoredEnvironments(
            Map.of("env", new Environment("env", List.of(assets))));
        SecretResolver secrets = new EnvironmentSecretResolver(environment::get);
        return new RequiredSecretsCheck(environments, secrets);
    }

    private static LLMPrometheusAsset prometheusAsset(String id, String username, String passwordEnv)
    {
        return new LLMPrometheusAsset(id, "env", id, "http://prometheus", null, username, passwordEnv, 0,
            new LLMPrometheusProfile(LLMProvider.mistralai, "m", "FRA", new LLMPrometheusProfile.DynamicProfile("q")));
    }

    private static LLMStaticAsset staticAsset()
    {
        return new LLMStaticAsset("s", "env", "S",
            new LLMStaticProfile(LLMProvider.mistralai, "m", "FRA", new LLMStaticProfile.RequestProfile(500, 1000)));
    }
}
