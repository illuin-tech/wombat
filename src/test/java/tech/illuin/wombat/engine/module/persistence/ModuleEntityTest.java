package tech.illuin.wombat.engine.module.persistence;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.FamilyType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModuleEntityTest
{
    private static final FamilyType ESTIMATOR = FamilyType.of("acme", "test", "estimator", ServiceFamily.LLM);

    @Test
    void advertise_keepsTheOrderingOfAModuleStayingInItsFamily()
    {
        ModuleEntity entity = ordered(ESTIMATOR, 2);

        entity.advertise(FamilyType.of(ESTIMATOR.namespace(), ESTIMATOR.localName(), ServiceFamily.LLM));

        assertEquals(2, entity.ordering);
    }

    @Test
    void advertise_dropsTheOrderingOfAModuleMovedToAnotherFamily()
    {
        ModuleEntity entity = ordered(ESTIMATOR, 2);

        entity.advertise(FamilyType.of(ESTIMATOR.namespace(), ESTIMATOR.localName(), ServiceFamily.KUBERNETES_CONTAINER));

        assertNull(entity.ordering);
    }

    @Test
    void advertise_dropsTheOrderingOfAFamilyModuleTurnedAssetType()
    {
        ModuleEntity entity = ordered(ESTIMATOR, 2);

        entity.advertise(AssetType.of(ESTIMATOR.namespace(), ESTIMATOR.localName(), ActivityRegime.MODELED, ServiceFamily.LLM));

        assertNull(entity.ordering);
    }

    private static ModuleEntity ordered(FamilyType type, int ordering)
    {
        ModuleEntity entity = ModuleEntity.registered(type);
        entity.ordering = ordering;
        return entity;
    }
}
