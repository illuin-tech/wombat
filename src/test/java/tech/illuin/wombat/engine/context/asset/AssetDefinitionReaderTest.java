package tech.illuin.wombat.engine.context.asset;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.engine.context.asset.model.UnrecognizedAsset;
import tech.illuin.wombat.engine.module.WombatModuleConfig;
import tech.illuin.wombat.engine.module.registry.ModuleRegistries;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;
import tech.illuin.wombat.module.llm_static.LLMStaticProfile;
import tech.illuin.wombat.module.llm_static.scenario.ConstantScenario;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.illuin.wombat.core.activity.commons.Country.FRANCE;

class AssetDefinitionReaderTest
{
    private static final String TYPE = LLMStaticModule.TYPE.name();
    private static final AssetIdentity IDENTITY = AssetIdentity.of("s1", "simulation:sim", TYPE);
    private static final JsonMapper MAPPER = new WombatModuleConfig().provideJsonMapper(List.of(new LLMStaticModule()));

    @Test
    void read_replacesTheIdentityTheDefinitionHolds()
    {
        Asset asset = reader(new LLMStaticModule()).read(IDENTITY, definition());

        assertInstanceOf(LLMStaticAsset.class, asset);
        assertEquals(IDENTITY, asset.identity());
    }

    @Test
    void read_keepsADefinitionWhoseModuleIsGone_asAnUnrecognizedAsset()
    {
        Asset asset = reader(new LLMStaticModule()).read(AssetIdentity.of("s1", "simulation:sim", "acme.gone"), definition());

        UnrecognizedAsset unrecognized = assertInstanceOf(UnrecognizedAsset.class, asset);
        assertEquals("acme.gone", unrecognized.rawType());
        assertTrue(unrecognized.rawJson().contains("\"profile\""), unrecognized.rawJson());
    }

    @Test
    void read_failsOnMalformedProperties()
    {
        ObjectNode definition = MAPPER.createObjectNode();
        definition.putObject("profile").put("models", "not-a-list");

        InvalidAssetException e = assertThrows(InvalidAssetException.class, () -> reader(new LLMStaticModule()).read(IDENTITY, definition));
        assertTrue(e.getMessage().startsWith("Invalid properties for asset s1 of type " + TYPE));
    }

    @Test
    void readValid_rejectsATypeWithoutModuleHere()
    {
        InvalidAssetException e = assertThrows(InvalidAssetException.class, () -> reader(new KubernetesAPIModule()).readValid(IDENTITY, definition()));
        assertTrue(e.getMessage().contains("No module"));
    }

    @Test
    void readValid_rejectsConstraintViolations_namingTheRequestedId()
    {
        ObjectNode definition = definition();
        definition.remove("profile");

        InvalidAssetException e = assertThrows(InvalidAssetException.class, () -> reader(new LLMStaticModule()).readValid(IDENTITY, definition));
        assertTrue(e.getMessage().startsWith("Invalid properties for asset s1: profile "));
    }

    @Test
    void readValid_returnsTheAssetUnderTheRequestedIdentity()
    {
        Asset asset = reader(new LLMStaticModule()).readValid(IDENTITY, definition());

        assertEquals(IDENTITY, asset.identity());
    }

    /**
     * @param module the only module the registry knows; the mapper always reads llm-static assets
     */
    private static AssetDefinitionReader reader(WombatModule module)
    {
        return new AssetDefinitionReader(
            new AssetValidator(ModuleRegistries.enabled(List.of(module))),
            Validation.buildDefaultValidatorFactory().getValidator(),
            MAPPER
        );
    }

    /* Stored under another identity, as a copy of a real asset is */
    private static ObjectNode definition()
    {
        return MAPPER.valueToTree(new LLMStaticAsset(
            AssetIdentity.of("a1", "e1", LLMStaticModule.TYPE),
            new LLMStaticProfile(LLMProvider.mistralai, "m", FRANCE, 1000, 500, List.of(new ConstantScenario()))
        ));
    }
}
