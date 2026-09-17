package tech.illuin.wombat.monitor;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetType;
import tech.illuin.wombat.core.secret.SecretAware;
import tech.illuin.wombat.module.WombatModuleConfig;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIAsset;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusAsset;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusModule;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The `.dist` template is what a developer copies to bootstrap a local configuration, so a key that drifted out of
 * sync with the asset records costs them a boot failure to discover. Parsing it through the real mapper keeps it
 * honest — it once carried `asset-id` where the records expect `id`, which silently yields assets with a null id.
 */
class MonitoredEnvironmentsTemplateTest
{
    private static final String TEMPLATE = "monitored-environments-test-template.yaml";

    @Test
    void theTemplateParsesIntoFullyPopulatedAssets()
    {
        List<Asset> assets = load().allAssets();

        assertEquals(3, assets.size());
        for (Asset asset : assets)
        {
            assertNotNull(asset.id(), () -> asset.type() + " parsed with a null id");
            assertEquals("preprod", asset.environmentId());
        }
    }

    @Test
    void theTemplateCoversEveryAssetType()
    {
        List<Asset> assets = load().allAssets();

        assertInstanceOf(KubernetesAPIAsset.class, byType(assets, AssetType.KUBERNETES_API));
        assertInstanceOf(LLMStaticAsset.class, byType(assets, AssetType.LLM_STATIC));
        assertInstanceOf(LLMPrometheusAsset.class, byType(assets, AssetType.LLM_PROMETHEUS));
    }

    @Test
    void theTemplateReferencesItsPrometheusPasswordByVariableName()
    {
        LLMPrometheusAsset asset = (LLMPrometheusAsset) byType(load().allAssets(), AssetType.LLM_PROMETHEUS);

        assertInstanceOf(SecretAware.class, asset);
        assertEquals(Set.of("WOMBAT_PROMETHEUS_PASSWORD"), asset.requiredSecretKeys());
    }

    private static MonitoredEnvironments load()
    {
        return new MonitorConfig().provideMonitoredEnvironments(new TemplateProperties(TEMPLATE), mapper());
    }

    /**
     * Built through the real producer: a bare {@code new YAMLMapper()} carries no asset subtypes, so every asset in
     * the template would fail on an unresolvable {@code type} discriminator rather than on anything the template says.
     * Only {@code type()} and {@code assetClass()} are read, so the modules' collaborators go unused.
     */
    private static YAMLMapper mapper()
    {
        return new WombatModuleConfig().provideYAMLMapper(List.of(
            new KubernetesAPIModule(),
            new LLMPrometheusModule(),
            new LLMStaticModule()
        ));
    }

    private static Asset byType(List<Asset> assets, AssetType type)
    {
        return assets.stream()
            .filter(asset -> asset.type() == type)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Template declares no " + type + " asset"));
    }

    private record TemplateProperties(String environmentsFile) implements MonitorProperties
    {
        @Override
        public String heartbeat()
        {
            return "0 0 * * * ?";
        }
    }
}
