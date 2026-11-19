package tech.illuin.wombat.engine.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.context.ResolvedContext;
import tech.illuin.wombat.core.context.WombatContext;
import tech.illuin.wombat.core.context.WombatContextProvider;
import tech.illuin.wombat.engine.context.asset.model.UnrecognizedAsset;
import tech.illuin.wombat.engine.context.asset.persistence.AssetEntity;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentEntity;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentRepository;

import java.util.ArrayList;
import java.util.List;

public class RepositoryContextProvider implements WombatContextProvider
{
    private final EnvironmentRepository repository;
    private final AssetRepository assetRepository;

    private static final Logger logger = LoggerFactory.getLogger(RepositoryContextProvider.class);

    public RepositoryContextProvider(EnvironmentRepository repository, AssetRepository assetRepository)
    {
        this.repository = repository;
        this.assetRepository = assetRepository;
    }

    @Override
    public WombatContext provide()
    {
        return new ResolvedContext(this.environments());
    }

    private List<Environment> environments()
    {
        List<Environment> result = new ArrayList<>();
        for (EnvironmentEntity entity : this.repository.findActive())
        {
            List<Asset> assets = this.assetRepository.findByEnvironment(entity.id).stream()
                .map(AssetEntity::toProperties)
                .filter(this::validateAsset)
                .toList();

            result.add(new Environment(entity.name, assets));
        }
        return result;
    }

    public boolean validateAsset(Asset asset)
    {
        if (asset instanceof UnrecognizedAsset unrecognized)
        {
            logger.debug(
                "Discarding unrecognized asset {} (type: {}) from active context for environment {}",
                unrecognized.identity().id(),
                unrecognized.rawType(),
                unrecognized.identity().environmentId()
            );
            return false;
        }
        return true;
    }
}
