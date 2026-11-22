package tech.illuin.wombat.engine.context.asset;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.engine.context.asset.model.UnrecognizedAsset;
import tech.illuin.wombat.engine.module.registry.InMemoryModuleRegistry;
import tech.illuin.wombat.engine.module.registry.ModuleRegistries;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;
import tech.illuin.wombat.module.llm_static.LLMStaticProfile;
import tech.illuin.wombat.module.llm_static.scenario.ConstantScenario;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.illuin.wombat.core.activity.commons.Country.FRANCE;

class AssetValidatorTest
{
    private static final LLMStaticAsset ASSET = new LLMStaticAsset(
        AssetIdentity.of("a1", "e1", LLMStaticModule.TYPE),
        new LLMStaticProfile(LLMProvider.mistralai, "m", FRANCE, 1000, 500, List.of(new ConstantScenario()))
    );

    @Test
    void acceptsAnAssetWithAnEnabledModule()
    {
        AssetValidator validator = new AssetValidator(ModuleRegistries.enabled(List.of(new LLMStaticModule())));

        assertDoesNotThrow(() -> validator.validate(ASSET));
    }

    @Test
    void acceptsAnAssetWithADisabledModule()
    {
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();
        registry.registerModule(new LLMStaticModule(), false);

        assertDoesNotThrow(() -> new AssetValidator(registry).validate(ASSET));
    }

    @Test
    void rejectsAnAssetWithoutModule()
    {
        AssetValidator validator = new AssetValidator(ModuleRegistries.enabled(List.of(new KubernetesAPIModule())));

        InvalidAssetException e = assertThrows(InvalidAssetException.class, () -> validator.validate(ASSET));
        assertTrue(e.getMessage().contains("No module"));
    }

    @Test
    void rejectsAnUnrecognizedAsset()
    {
        AssetValidator validator = new AssetValidator(ModuleRegistries.enabled(List.of(new LLMStaticModule())));
        UnrecognizedAsset unrecognized = new UnrecognizedAsset(AssetIdentity.of("u", "e1", "tech.illuin.unknown"), "{}");

        InvalidAssetException e = assertThrows(InvalidAssetException.class, () -> validator.validate(unrecognized));
        assertTrue(e.getMessage().contains("Unknown asset type"));
    }
}
