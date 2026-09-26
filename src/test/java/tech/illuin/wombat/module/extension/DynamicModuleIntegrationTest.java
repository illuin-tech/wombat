package tech.illuin.wombat.module.extension;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.illuin.wombat.context.persistence.AssetConverter;
import tech.illuin.wombat.context.persistence.AssetEntity;
import tech.illuin.wombat.core.WombatCore;
import tech.illuin.wombat.core.asset.ActivityRegime;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetType;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.asset.ServiceFamily;
import tech.illuin.wombat.core.asset.profile.Profile;
import tech.illuin.wombat.core.context.ResolvedContext;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.core.source.Monitorable;
import tech.illuin.wombat.core.source.WombatSource;
import tech.illuin.wombat.module.WombatModuleConfig;
import tech.illuin.wombat.monitor.MonitoredEnvironments;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DynamicModuleIntegrationTest
{
    @Test
    void dynamicModule_e2e_discovery_jackson_persistence_core(@TempDir Path tempDir) throws Exception
    {
        Path jarFile = tempDir.resolve("custom-extension.jar");
        createExtensionJar(jarFile, CustomDynamicModule.class, CustomDynamicAsset.class);

        try (DynamicExtensionLoader loader = new DynamicExtensionLoader(tempDir))
        {
            List<WombatModule> modules = loader.load();
            assertEquals(1, modules.size());
            WombatModule dynamicModule = modules.getFirst();

            assertEquals("tech.custom.extension.custom-source", dynamicModule.type().name());

            // 1. YAML Deserialization via WombatModuleConfig
            WombatModuleConfig moduleConfig = new WombatModuleConfig();
            YAMLMapper yamlMapper = moduleConfig.provideYAMLMapper(modules);

            String yaml = """
                environments:
                  dynamic-env:
                    id: dynamic-env
                    assets:
                      - type: tech.custom.extension.custom-source
                        id: dyn-asset-1
                        environment-id: dynamic-env
                        name: Dynamic Asset One
                        endpoint: http://custom-source:8080
                        heartbeat-skip: 1
                """;

            MonitoredEnvironments envs = yamlMapper.readValue(yaml, MonitoredEnvironments.class);
            assertEquals(1, envs.environments().size());
            Environment env = envs.environments().get("dynamic-env");
            assertNotNull(env);
            assertEquals(1, env.assets().size());

            Asset parsedAsset = env.assets().getFirst();
            assertInstanceOf(CustomDynamicAsset.class, parsedAsset);
            CustomDynamicAsset customAsset = (CustomDynamicAsset) parsedAsset;
            assertEquals("dyn-asset-1", customAsset.id());
            assertEquals("http://custom-source:8080", customAsset.endpoint());
            assertEquals("tech.custom.extension.custom-source", customAsset.type().name());

            // 2. Entity Persistence & Converter Round-trip
            AssetEntity entity = AssetEntity.from("dynamic-env", customAsset);
            assertEquals("tech.custom.extension.custom-source", entity.type);
            assertEquals(customAsset, entity.toProperties());

            ObjectMapper jsonMapper = moduleConfig.provideJsonMapper(modules);
            AssetConverter converter = new AssetConverter((com.fasterxml.jackson.databind.json.JsonMapper) jsonMapper);
            String dbJson = converter.convertToDatabaseColumn(customAsset);
            assertTrue(dbJson.contains("\"type\":\"tech.custom.extension.custom-source\""));

            Asset restoredAsset = converter.convertToEntityAttribute(dbJson);
            assertEquals(customAsset, restoredAsset);

            // 3. WombatCore Registration & Execution
            try (WombatCore core = new WombatCore(
                () -> new ResolvedContext(List.of(env)),
                metrics -> {},
                List.of(dynamicModule),
                defaults -> {}
            ); AssetMonitor monitor = core.createMonitor()) {
                monitor.trigger();
            }

            Object src = dynamicModule.getClass().getMethod("getSource").invoke(dynamicModule);
            AtomicBoolean sampledFlag = (AtomicBoolean) src.getClass().getField("sampled").get(src);
            assertTrue(sampledFlag.get());
        }
    }

    @Test
    void dynamicModule_whenDropped_databaseRowGracefullyIgnored(@TempDir Path tempDir) throws Exception
    {
        Path jarFile = tempDir.resolve("custom-extension.jar");
        createExtensionJar(jarFile, CustomDynamicModule.class, CustomDynamicAsset.class);

        String dbJson;
        try (DynamicExtensionLoader loader = new DynamicExtensionLoader(tempDir))
        {
            List<WombatModule> modules = loader.load();
            WombatModuleConfig moduleConfig = new WombatModuleConfig();
            ObjectMapper jsonMapper = moduleConfig.provideJsonMapper(modules);
            AssetConverter converter = new AssetConverter((com.fasterxml.jackson.databind.json.JsonMapper) jsonMapper);

            CustomDynamicAsset customAsset = new CustomDynamicAsset("dyn-asset-1", "dynamic-env", "Dynamic Asset One", "http://custom-source:8080", 1);
            dbJson = converter.convertToDatabaseColumn(customAsset);
        }

        // Now simulate the JAR being removed/dropped: standard modules only
        WombatModuleConfig standardConfig = new WombatModuleConfig();
        ObjectMapper standardJsonMapper = standardConfig.provideJsonMapper(Collections.emptyList());
        AssetConverter standardConverter = new AssetConverter((com.fasterxml.jackson.databind.json.JsonMapper) standardJsonMapper);

        Asset parsedAsset = standardConverter.convertToEntityAttribute(dbJson);
        assertInstanceOf(tech.illuin.wombat.context.model.UnrecognizedAsset.class, parsedAsset);

        tech.illuin.wombat.context.model.UnrecognizedAsset unrecognized = (tech.illuin.wombat.context.model.UnrecognizedAsset) parsedAsset;
        assertEquals("dyn-asset-1", unrecognized.id());
        assertEquals("Dynamic Asset One", unrecognized.name());
        assertEquals("tech.custom.extension.custom-source", unrecognized.rawType());
        assertEquals(dbJson, standardConverter.convertToDatabaseColumn(unrecognized));

        // YAML recovery verification for dropped module
        YAMLMapper standardYamlMapper = standardConfig.provideYAMLMapper(Collections.emptyList());
        String yaml = """
            environments:
              dynamic-env:
                id: dynamic-env
                assets:
                  - type: tech.custom.extension.custom-source
                    id: dyn-asset-1
                    environment-id: dynamic-env
                    name: Dynamic Asset One
            """;
        MonitoredEnvironments monitoredEnvironments = standardYamlMapper.readValue(yaml, MonitoredEnvironments.class);
        assertNotNull(monitoredEnvironments);
        assertEquals(1, monitoredEnvironments.allAssets().size());
        Asset yamlAsset = monitoredEnvironments.allAssets().getFirst();
        assertInstanceOf(tech.illuin.wombat.context.model.UnrecognizedAsset.class, yamlAsset);
        tech.illuin.wombat.context.model.UnrecognizedAsset unrecognizedYaml = (tech.illuin.wombat.context.model.UnrecognizedAsset) yamlAsset;
        assertEquals("dyn-asset-1", unrecognizedYaml.id());
        assertEquals("dynamic-env", unrecognizedYaml.environmentId());
        assertEquals("Dynamic Asset One", unrecognizedYaml.name());
        assertEquals("tech.custom.extension.custom-source", unrecognizedYaml.rawType());
    }

    private static void createExtensionJar(Path jarPath, Class<? extends WombatModule> moduleClass, Class<? extends Asset> assetClass) throws Exception
    {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile())))
        {
            JarEntry spiEntry = new JarEntry("META-INF/services/" + WombatModule.class.getName());
            jos.putNextEntry(spiEntry);
            jos.write((moduleClass.getName() + "\n").getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();

            addClass(jos, moduleClass);
            if (assetClass != null)
                addClass(jos, assetClass);
            addClass(jos, CustomDynamicSource.class);
        }
    }

    private static void addClass(JarOutputStream jos, Class<?> clazz) throws Exception
    {
        String path = clazz.getName().replace('.', '/') + ".class";
        JarEntry entry = new JarEntry(path);
        jos.putNextEntry(entry);
        try (InputStream is = clazz.getClassLoader().getResourceAsStream(path))
        {
            if (is != null)
                is.transferTo(jos);
        }
        jos.closeEntry();
    }

    public static final class CustomDynamicModule implements WombatModule
    {
        public static final AssetType TYPE = AssetType.of("tech.custom.extension", "custom-source", ActivityRegime.MEASURED, ServiceFamily.LLM);
        public final CustomDynamicSource source = new CustomDynamicSource();

        @Override
        public AssetType type()
        {
            return TYPE;
        }

        @Override
        public Class<? extends Asset> assetClass()
        {
            return CustomDynamicAsset.class;
        }

        @Override
        public Optional<WombatSource> createSource(tech.illuin.wombat.core.context.WombatContext context)
        {
            return Optional.of(this.source);
        }

        public CustomDynamicSource getSource()
        {
            return this.source;
        }
    }

    public static final class CustomDynamicSource implements WombatSource
    {
        public final AtomicBoolean sampled = new AtomicBoolean(false);

        @Override
        public List<tech.illuin.wombat.core.source.data.MetricData> source(java.time.Instant heartbeat, Asset asset)
        {
            this.sampled.set(true);
            return Collections.emptyList();
        }
    }

    public record CustomDynamicAsset(
        @JsonProperty("id") String id,
        @JsonProperty("environment-id") String environmentId,
        @JsonProperty("name") String name,
        @JsonProperty("endpoint") String endpoint,
        @JsonProperty("heartbeat-skip") int heartbeatSkip
    ) implements Asset, Monitorable
    {
        @Override
        public AssetType type()
        {
            return CustomDynamicModule.TYPE;
        }

        @Override
        public Profile profile()
        {
            return null;
        }
    }
}
