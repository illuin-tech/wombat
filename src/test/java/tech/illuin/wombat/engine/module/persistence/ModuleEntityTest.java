package tech.illuin.wombat.engine.module.persistence;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.FamilyType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModuleEntityTest
{
    @Test
    void advertise_keepsTheOrdering_whateverTypeTheModuleAdvertisesNow()
    {
        ModuleEntity entity = ModuleEntity.registered(FamilyType.of("acme", "test", "estimator", ServiceFamily.LLM));
        entity.ordering = 2;

        entity.advertise(FamilyType.of("acme", "test", "estimator", ServiceFamily.KUBERNETES_CONTAINER));
        entity.advertise(AssetType.of("acme", "test", "estimator", ActivityRegime.MODELED, ServiceFamily.LLM));

        assertEquals(2, entity.ordering);
    }
}
