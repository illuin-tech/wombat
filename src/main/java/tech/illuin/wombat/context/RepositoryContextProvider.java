package tech.illuin.wombat.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.context.model.UnrecognizedAsset;
import tech.illuin.wombat.context.persistence.AssetEntity;
import tech.illuin.wombat.context.persistence.AssetRepository;
import tech.illuin.wombat.context.persistence.EnvironmentEntity;
import tech.illuin.wombat.context.persistence.EnvironmentRepository;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.context.ResolvedContext;
import tech.illuin.wombat.core.context.WombatContext;
import tech.illuin.wombat.core.context.WombatContextProvider;
import tech.illuin.wombat.core.secret.SecretResolver;

import java.util.ArrayList;
import java.util.List;

public class RepositoryContextProvider implements WombatContextProvider
{
    private final EnvironmentRepository repository;
    private final AssetRepository assetRepository;
    private final SecretResolver secrets;

    private static final Logger logger = LoggerFactory.getLogger(RepositoryContextProvider.class);

    public RepositoryContextProvider(EnvironmentRepository repository, AssetRepository assetRepository, SecretResolver secrets)
    {
        this.repository = repository;
        this.assetRepository = assetRepository;
        this.secrets = secrets;
    }

    @Override
    public WombatContext provide()
    {
        // The assets come from the database, the credentials they reference from the environment — the two are
        // resolved together so a module never has to reach outside the context for either.
        return new ResolvedContext(this.environments(), this.secrets);
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
            logger.debug("Discarding unrecognized asset {} (type: {}) from active context for environment {}", unrecognized.id(), unrecognized.rawType(), unrecognized.environmentId());
            return false;
        }
        return true;
    }
}
