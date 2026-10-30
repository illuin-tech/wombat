package tech.illuin.wombat.engine.module.extension;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.profile.AssetProfile;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.module.WombatModule;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExtensionContractCheckTest
{
    private static final AssetType VALID_TYPE = AssetType.of("tech.test", "sample", ActivityRegime.MODELED, ServiceFamily.LLM);

    @Test
    void check_validModule_passes()
    {
        assertTrue(ExtensionContractCheck.check(new TestModule(VALID_TYPE, TestAsset.class)).isEmpty());
    }

    @Test
    void check_nullType_throwsIllegalArgumentException()
    {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> ExtensionContractCheck.check(new TestModule(null, TestAsset.class))
        );
        assertTrue(exception.getMessage().contains("has invalid AssetType"));
    }

    @Test
    void check_blankTypeName_throwsIllegalArgumentException()
    {
        AssetType blankType = mock(AssetType.class);
        when(blankType.name()).thenReturn("   ");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> ExtensionContractCheck.check(new TestModule(blankType, TestAsset.class))
        );
        assertTrue(exception.getMessage().contains("has invalid AssetType"));
    }

    @Test
    void check_nullAssetClass_throwsIllegalArgumentException()
    {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> ExtensionContractCheck.check(new TestModule(VALID_TYPE, null))
        );
        assertTrue(exception.getMessage().contains("has null assetClass"));
    }

    private record TestModule(AssetType type, Class<? extends Asset> assetClass) implements WombatModule
    {
    }

    public record TestAsset(AssetIdentity identity, AssetType type, AssetProfile profile) implements Asset
    {
    }
}
