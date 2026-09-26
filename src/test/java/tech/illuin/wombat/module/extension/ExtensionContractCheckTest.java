package tech.illuin.wombat.module.extension;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.activity.commons.ActivityData;
import tech.illuin.wombat.core.asset.ActivityRegime;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetType;
import tech.illuin.wombat.core.asset.ServiceFamily;
import tech.illuin.wombat.core.asset.profile.LLMProfile;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.asset.profile.Profile;
import tech.illuin.wombat.core.evaluation.WombatEvaluationResolver;
import tech.illuin.wombat.core.evaluation.cost.commons.AssetCost;
import tech.illuin.wombat.core.module.WombatModule;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionContractCheckTest
{
    private static final AssetType LLM_TYPE = AssetType.of("tech.test", "llm", ActivityRegime.MODELED, ServiceFamily.LLM);
    private static final AssetType K8S_TYPE = AssetType.of("tech.test", "k8s", ActivityRegime.MEASURED, ServiceFamily.KUBERNETES_CONTAINER);

    @Test
    void check_compatibleProfile_passes()
    {
        assertTrue(ExtensionContractCheck.check(new TestModule(LLM_TYPE, CompatibleAsset.class, false)).isEmpty());
    }

    @Test
    void check_incompatibleFinalProfile_reportsMismatch()
    {
        Optional<String> warning = ExtensionContractCheck.check(new TestModule(LLM_TYPE, IncompatibleAsset.class, false));
        assertTrue(warning.isPresent());
        assertTrue(warning.get().contains(PlainProfile.class.getName()));
        assertTrue(warning.get().contains(LLMProfile.class.getName()));
    }

    @Test
    void check_incompatibleProfileForKubernetesFamily_reportsMismatch()
    {
        assertTrue(ExtensionContractCheck.check(new TestModule(K8S_TYPE, IncompatibleAsset.class, false)).isPresent());
    }

    @Test
    void check_nonFinalDeclaredProfile_passes()
    {
        assertTrue(ExtensionContractCheck.check(new TestModule(LLM_TYPE, GenericAsset.class, false)).isEmpty());
    }

    @Test
    void check_ownImpactResolver_passes()
    {
        assertTrue(ExtensionContractCheck.check(new TestModule(LLM_TYPE, IncompatibleAsset.class, true)).isEmpty());
    }

    private record TestModule(AssetType type, Class<? extends Asset> assetClass, boolean ownResolver) implements WombatModule
    {
        @Override
        public Optional<WombatEvaluationResolver> createImpactResolver()
        {
            if (!this.ownResolver)
                return Optional.empty();
            return Optional.of((Asset asset, ActivityData _) -> new AssetCost(asset.environmentId(), asset.id()));
        }
    }

    public record PlainProfile(String id) implements Profile {}

    public record TestLLMProfile(LLMProvider provider, String model, String location) implements LLMProfile {}

    public record CompatibleAsset(String id, String environmentId, String name, AssetType type, TestLLMProfile profile) implements Asset {}

    public record IncompatibleAsset(String id, String environmentId, String name, AssetType type, PlainProfile profile) implements Asset {}

    public record GenericAsset(String id, String environmentId, String name, AssetType type, Profile profile) implements Asset {}
}
