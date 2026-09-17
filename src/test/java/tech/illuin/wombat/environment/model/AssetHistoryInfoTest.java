package tech.illuin.wombat.environment.model;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.context.model.AssetHistoryInfo;
import tech.illuin.wombat.context.persistence.AssetConfigAction;
import tech.illuin.wombat.context.persistence.AssetConfigHistory;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssetHistoryInfoTest
{

    @Test
    void mapsActionAndTimestampAndParsesTheSnapshotIntoJson()
    {
        AssetConfigHistory entity = new AssetConfigHistory();
        entity.assetId = "a1";
        entity.environmentId = "e1";
        entity.action = AssetConfigAction.CREATE;
        entity.changedAt = Instant.ofEpochMilli(5);
        entity.snapshot = "{\"id\":\"a1\",\"name\":\"Asset 1\",\"type\":\"LLM_STATIC\"}";

        AssetHistoryInfo info = AssetHistoryInfo.from(entity);

        assertEquals(AssetConfigAction.CREATE, info.action());
        assertEquals(Instant.ofEpochMilli(5), info.changedAt());
        assertEquals("a1", info.snapshot().get("id").asText());
        assertEquals("Asset 1", info.snapshot().get("name").asText());
    }

    @Test
    void failsFastWhenTheStoredSnapshotIsNotValidJson()
    {
        AssetConfigHistory entity = new AssetConfigHistory();
        entity.assetId = "a1";
        entity.action = AssetConfigAction.UPDATE;
        entity.changedAt = Instant.ofEpochMilli(5);
        entity.snapshot = "not-json";

        assertThrows(IllegalStateException.class, () -> AssetHistoryInfo.from(entity));
    }
}
