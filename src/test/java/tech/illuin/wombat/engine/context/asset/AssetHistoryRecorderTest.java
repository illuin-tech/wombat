package tech.illuin.wombat.engine.context.asset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConfigAction;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConfigHistory;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConfigHistoryRepository;
import tech.illuin.wombat.engine.context.asset.persistence.AssetEntity;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;
import tech.illuin.wombat.module.llm_static.LLMStaticProfile;
import tech.illuin.wombat.module.llm_static.scenario.ConstantScenario;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static tech.illuin.wombat.core.activity.commons.Country.FRANCE;

class AssetHistoryRecorderTest
{
    @Test
    void persistsASnapshotOfTheAsset()
    {
        AssetConfigHistoryRepository repository = mock(AssetConfigHistoryRepository.class);
        AssetEntity entity = AssetEntity.from("e1", new LLMStaticAsset(
            AssetIdentity.of("a1", "e1", "A1", LLMStaticModule.TYPE),
            new LLMStaticProfile(LLMProvider.mistralai, "m", FRANCE, List.of(new ConstantScenario(500, 1000)))
        ));

        new AssetHistoryRecorder(repository).record(AssetConfigAction.UPDATE, entity);

        ArgumentCaptor<AssetConfigHistory> captor = ArgumentCaptor.forClass(AssetConfigHistory.class);
        verify(repository).persist(captor.capture());
        AssetConfigHistory history = captor.getValue();
        assertEquals(AssetConfigAction.UPDATE, history.action);
        assertEquals("a1", history.assetId);
        assertEquals("e1", history.environmentId);
        assertTrue(history.snapshot.contains("\"id\":\"a1\""));
        assertTrue(history.snapshot.contains("\"type\":\"" + LLMStaticModule.TYPE.name() + "\""));
    }
}
