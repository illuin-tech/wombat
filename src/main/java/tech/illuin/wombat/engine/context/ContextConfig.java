package tech.illuin.wombat.engine.context;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Produces;
import tech.illuin.wombat.core.context.WombatContextProvider;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentRepository;

@ApplicationScoped
public class ContextConfig
{
    @Produces @Singleton
    public WombatContextProvider provideActiveEnvironments(
        EnvironmentRepository repository,
        AssetRepository assetRepository
    ) {
        return new RepositoryContextProvider(repository, assetRepository);
    }
}
