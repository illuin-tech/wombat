package tech.illuin.wombat.engine.module.settings;

import io.quarkus.runtime.configuration.DurationConverter;
import io.smallrye.config.EnvConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.type.FamilyType;
import tech.illuin.wombat.core.asset.type.ModuleType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.module.ModuleSettings;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.secret.SecretKey;
import tech.illuin.wombat.engine.module.WombatModuleConfig;
import tech.illuin.wombat.engine.module.extension.WombatModuleProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleSettingsReaderTest
{
    private static final ModuleType PROBE = FamilyType.of("test", "dashed-probe", ServiceFamily.KUBERNETES_CONTAINER);
    private static final ModuleType BARE = FamilyType.of("test", "bare", ServiceFamily.KUBERNETES_CONTAINER);
    private static final List<WombatModule> MODULES = List.of(new ProbeModule(), new BareModule());

    @Test
    void readsTheSettingsOfAModuleFromYaml()
    {
        Config config = config("""
            wombat:
              module:
                settings:
                  test.dashed-probe:
                    uri: "http://probe:5000/"
                    proxy-url: "http://proxy:3128"
                    port: 8080
            """, Map.of());

        assertEquals(List.of(settings("http://probe:5000/", "http://proxy:3128", 8080, null, null, null, null)), read(config));
    }

    @Test
    void readsEnvironmentVariablesOverridingTheKeysOfAFile()
    {
        Config config = config("""
            wombat:
              module:
                settings:
                  test.dashed-probe:
                    uri: "http://probe:5000/"
                    proxy-url: "http://overridden:3128"
                    timeouts:
                      read-seconds: 10
            """, Map.of(
            "WOMBAT_MODULE_SETTINGS__TEST_DASHED_PROBE__PROXY_URL", "http://proxy:3128",
            "WOMBAT_MODULE_SETTINGS__TEST_DASHED_PROBE__TIMEOUTS_READ_SECONDS", "30"
        ));

        assertEquals(List.of(settings("http://probe:5000/", "http://proxy:3128", null, new Timeouts(null, 30), null, null, null)), read(config));
    }

    @Test
    void readsNestedRecordsListsAndValueTypes()
    {
        Config config = config("""
            wombat:
              module:
                settings:
                  test.dashed-probe:
                    uri: "http://probe:5000/"
                    timeouts:
                      connect-seconds: 5
                    tags: [ "a", "b" ]
                    mirrors:
                      - host: "mirror-1"
                      - host: "mirror-2"
                        port: 8081
                    token: "probe-token"
            """, Map.of());

        assertEquals(
            List.of(settings(
                "http://probe:5000/", null, null,
                new Timeouts(5, null),
                List.of("a", "b"),
                List.of(new Endpoint("mirror-1", null), new Endpoint("mirror-2", 8081)),
                new SecretKey("probe-token")
            )),
            read(config)
        );
    }

    @Test
    void givesNoSettingsToAModuleGivenNone()
    {
        assertEquals(List.of(), read(config("", Map.of())));
    }

    @Test
    void ignoresAModuleThatIsNotDetected()
    {
        Config config = config("""
            wombat:
              module:
                settings:
                  acme.removed:
                    uri: "http://removed/"
            """, Map.of());

        assertEquals(List.of(), read(config));
    }

    @Test
    void failsNamingTheKeyOfEveryProblemAtOnce()
    {
        Config config = config("""
            wombat:
              module:
                settings:
                  test.dashed-probe:
                    uri: "http://probe:5000/"
                    port: "eighty"
                    colour: "red"
                  test.bare:
                    uri: "http://bare/"
            """, Map.of());

        ModuleSettingsException exception = assertThrows(ModuleSettingsException.class, () -> read(config));

        /* Jackson stops at the first property a module cannot use */
        List<String> problems = exception.problems().stream().sorted().toList();
        assertEquals(2, problems.size(), exception.getMessage());
        assertEquals("wombat.module.settings.\"test.bare\": module test.bare takes no settings", problems.get(0));
        assertTrue(problems.get(1).startsWith("wombat.module.settings.\"test.dashed-probe\"."), problems.get(1));
    }

    @Test
    void failsOnAPropertyThatIsNotASetting()
    {
        Config config = config("""
            wombat:
              module:
                settings:
                  test.dashed-probe:
                    uri: "http://probe:5000/"
                    colour: "red"
            """, Map.of());

        ModuleSettingsException exception = assertThrows(ModuleSettingsException.class, () -> read(config));

        assertEquals(List.of("wombat.module.settings.\"test.dashed-probe\".colour: not a setting of this module"), exception.problems());
    }

    @Test
    void failsOnAMissingRequiredProperty()
    {
        Config config = config("""
            wombat:
              module:
                settings:
                  test.dashed-probe:
                    proxy-url: "http://proxy:3128"
            """, Map.of());

        ModuleSettingsException exception = assertThrows(ModuleSettingsException.class, () -> read(config));

        assertEquals(List.of("wombat.module.settings.\"test.dashed-probe\": uri cannot be null"), exception.problems());
    }

    @Test
    void failsOnABrokenConstraint()
    {
        Config config = config("""
            wombat:
              module:
                settings:
                  test.dashed-probe:
                    uri: "http://probe:5000/"
                    timeouts:
                      read-seconds: -1
                    mirrors:
                      - host: "mirror"
                        port: 0
            """, Map.of());

        ModuleSettingsException exception = assertThrows(ModuleSettingsException.class, () -> read(config));

        /* The messages follow the locale */
        assertEquals(
            List.of(
                "wombat.module.settings.\"test.dashed-probe\".mirrors[0].port",
                "wombat.module.settings.\"test.dashed-probe\".timeouts.read-seconds"
            ),
            exception.problems().stream().map(problem -> problem.substring(0, problem.indexOf(": "))).sorted().toList()
        );
    }

    private static List<ModuleSettings> read(Config config)
    {
        return new ModuleSettingsReader(
            config.unwrap(SmallRyeConfig.class).getConfigMapping(WombatModuleProperties.class, "wombat.module").settings(),
            MODULES,
            new WombatModuleConfig().provideYAMLMapper(MODULES),
            Validation.buildDefaultValidatorFactory().getValidator()
        ).read();
    }

    /**
     * Read through the {@code wombat.module} mapping, as the app does.
     */
    private static Config config(String yaml, Map<String, String> env)
    {
        return new SmallRyeConfigBuilder()
            .withMapping(WombatModuleProperties.class, "wombat.module")
            .withConverter(Duration.class, 100, new DurationConverter())
            .withSources(new YamlConfigSource("application.yaml", yaml, 250))
            .withSources(new EnvConfigSource(env, 300))
            .build();
    }

    private static ProbeSettings settings(
        String uri,
        String proxyUrl,
        Integer port,
        Timeouts timeouts,
        List<String> tags,
        List<Endpoint> mirrors,
        SecretKey token
    ) {
        return new ProbeSettings(uri, proxyUrl, port, timeouts, tags, mirrors, token);
    }

    /* Each shape the reader handles: values, a nested record, a list of values, a list of records, a value type */
    private record ProbeSettings(
        @NotBlank String uri,
        String proxyUrl,
        Integer port,
        @Valid Timeouts timeouts,
        List<String> tags,
        List<@Valid Endpoint> mirrors,
        SecretKey token
    ) implements ModuleSettings
    {
        ProbeSettings
        {
            Objects.requireNonNull(uri, "uri cannot be null");
        }

        @Override
        public ModuleType type()
        {
            return PROBE;
        }
    }

    private record Timeouts(Integer connectSeconds, @Positive Integer readSeconds) {}

    private record Endpoint(@NotBlank String host, @Positive Integer port) {}

    private record ProbeModule() implements WombatModule
    {
        @Override
        public ModuleType type()
        {
            return PROBE;
        }

        @Override
        public Optional<Class<? extends ModuleSettings>> settingsClass()
        {
            return Optional.of(ProbeSettings.class);
        }
    }

    /* A module declaring no settings */
    private record BareModule() implements WombatModule
    {
        @Override
        public ModuleType type()
        {
            return BARE;
        }
    }
}
