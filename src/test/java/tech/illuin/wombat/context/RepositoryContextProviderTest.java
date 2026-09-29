package tech.illuin.wombat.context;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.context.model.UnrecognizedAsset;
import tech.illuin.wombat.context.persistence.AssetEntity;
import tech.illuin.wombat.context.persistence.AssetRepository;
import tech.illuin.wombat.context.persistence.EnvironmentEntity;
import tech.illuin.wombat.context.persistence.EnvironmentRepository;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.context.WombatContext;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticProfile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepositoryContextProviderTest
{
    private final EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
    private final AssetRepository assetRepository = mock(AssetRepository.class);
    private final SecretResolver secretResolver = mock(SecretResolver.class);

    @Test
    void provide_filtersOutUnrecognizedAssetsFromActiveContext()
    {
        EnvironmentEntity envEntity = new EnvironmentEntity();
        envEntity.id = "env-1";
        envEntity.name = "Environment 1";

        when(this.environmentRepository.findActive()).thenReturn(List.of(envEntity));

        LLMStaticAsset validAsset = new LLMStaticAsset(
            AssetIdentity.of("valid-1", "env-1", "Valid Static"),
            new LLMStaticProfile(LLMProvider.mistralai, "mistral-tiny", "FRA", new LLMStaticProfile.RequestProfile(100, 200))
        );
        AssetEntity validEntity = AssetEntity.from("env-1", validAsset);

        UnrecognizedAsset unrecognizedAsset = new UnrecognizedAsset(
            AssetIdentity.of("unrec-1", "env-1", "Dropped Module Asset"), "tech.illuin.dropped.Asset", "{\"id\":\"unrec-1\"}"
        );
        AssetEntity unrecognizedEntity = new AssetEntity();
        unrecognizedEntity.id = "unrec-1";
        unrecognizedEntity.environmentId = "env-1";
        unrecognizedEntity.name = "Dropped Module Asset";
        unrecognizedEntity.type = "tech.illuin.dropped.Asset";
        unrecognizedEntity.properties = unrecognizedAsset;

        when(this.assetRepository.findByEnvironment("env-1")).thenReturn(List.of(validEntity, unrecognizedEntity));

        RepositoryContextProvider provider = new RepositoryContextProvider(this.environmentRepository, this.assetRepository, this.secretResolver);
        WombatContext context = provider.provide();

        List<Environment> environments = context.environments();
        assertEquals(1, environments.size());
        Environment env = environments.getFirst();
        assertEquals("Environment 1", env.id());
        assertEquals(1, env.assets().size());
        assertEquals(validAsset, env.assets().getFirst());
    }
}
