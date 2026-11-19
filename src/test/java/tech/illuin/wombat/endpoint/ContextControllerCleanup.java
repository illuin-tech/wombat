package tech.illuin.wombat.endpoint;

import io.quarkus.narayana.jta.QuarkusTransaction;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConfigHistoryRepository;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentRepository;

import java.util.List;

/** Removes the environments and assets a controller test created through the API, with their history. */
final class ContextControllerCleanup
{
    private ContextControllerCleanup() {}

    static void clean(
        EnvironmentRepository environments, AssetRepository assets, AssetConfigHistoryRepository history,
        List<String> environmentIds, List<String> assetIds
    ) {
        QuarkusTransaction.requiringNew().run(() -> {
            for (String id : assetIds)
            {
                assets.findByIdOptional(id).ifPresent(assets::delete);
                history.findByAsset(id).forEach(history::delete);
            }
            for (String id : environmentIds)
                environments.findByIdOptional(id).ifPresent(environments::delete);
        });
    }
}
