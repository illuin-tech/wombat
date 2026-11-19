package tech.illuin.wombat.engine.context.asset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.enterprise.context.ApplicationScoped;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConfigAction;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConfigHistory;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConfigHistoryRepository;
import tech.illuin.wombat.engine.context.asset.persistence.AssetEntity;

/**
 * Appends a snapshot of an asset to its {@link AssetConfigHistory}, for every change: those applied by the
 * {@link tech.illuin.wombat.engine.context.EnvironmentReconciler} and those made through the API.
 */
@ApplicationScoped
public class AssetHistoryRecorder
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final AssetConfigHistoryRepository repository;

    public AssetHistoryRecorder(AssetConfigHistoryRepository repository)
    {
        this.repository = repository;
    }

    public void record(AssetConfigAction action, AssetEntity entity)
    {
        this.repository.persist(AssetConfigHistory.of(action, entity, snapshot(entity)));
    }

    private static String snapshot(AssetEntity entity)
    {
        try {
            return MAPPER.writeValueAsString(new AssetSnapshot(entity.id, entity.name, entity.type, entity.properties));
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize asset config snapshot for " + entity.id, e);
        }
    }

    private record AssetSnapshot(
        String id,
        String name,
        String type,
        Asset data
    ) {}
}
