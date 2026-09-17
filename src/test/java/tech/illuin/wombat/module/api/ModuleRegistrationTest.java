package tech.illuin.wombat.module.api;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetType;
import tech.illuin.wombat.core.module.WombatModule;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleRegistrationTest
{

    @Test
    void of_singleModule_isEnabledAndCarriesIt()
    {
        RecordingModule module = new RecordingModule();

        ModuleRegistration registration = ModuleRegistration.of("kubernetes-api", module);

        assertEquals("kubernetes-api", registration.id());
        assertTrue(registration.enabled());
        assertEquals(1, registration.modules().size());
        assertTrue(registration.modules().contains(module));
    }

    @Test
    void of_collection_carriesEveryModule()
    {
        RecordingModule first = new RecordingModule();
        RecordingModule second = new RecordingModule();

        ModuleRegistration registration = ModuleRegistration.of("bundle", List.of(first, second));

        assertTrue(registration.enabled());
        assertEquals(2, registration.modules().size());
    }

    @Test
    void disabled_keepsTheIdButCarriesNoModule()
    {
        // A disabled registration is still produced as a bean, so consumers see the id and skip it deliberately
        // rather than the module silently going missing.
        ModuleRegistration registration = ModuleRegistration.disabled("llm-prometheus");

        assertEquals("llm-prometheus", registration.id());
        assertFalse(registration.enabled());
        assertTrue(registration.modules().isEmpty());
    }

    @Test
    void modules_areACopyTheCallerCannotMutate()
    {
        ModuleRegistration registration = ModuleRegistration.of("kubernetes-api", new RecordingModule());

        assertThrows(UnsupportedOperationException.class, () -> registration.modules().add(new RecordingModule()));
    }

    @Test
    void close_closesEveryModuleItCarries()
    {
        RecordingModule first = new RecordingModule();
        RecordingModule second = new RecordingModule();
        CompositeModuleRegistration registration =
            (CompositeModuleRegistration) ModuleRegistration.of("bundle", List.of(first, second));

        registration.close();

        assertTrue(first.closed);
        assertTrue(second.closed);
    }

    private static final class RecordingModule implements WombatModule
    {
        private boolean closed;

        @Override
        public AssetType type()
        {
            return AssetType.KUBERNETES_API;
        }

        @Override
        public Class<? extends Asset> assetClass()
        {
            return Asset.class;
        }

        @Override
        public void close()
        {
            this.closed = true;
        }
    }
}
