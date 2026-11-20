package tech.illuin.wombat.engine.module.persistence;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ModuleRepositoryTest
{
    private static final String DETECTED = KubernetesSimulatedModule.TYPE.name();
    private static final AssetType NEW_TYPE = AssetType.of("acme", "new-extension", ActivityRegime.MEASURED, ServiceFamily.LLM);
    private static final String NEW = NEW_TYPE.name();

    @Inject
    ModuleRepository repository;

    @AfterEach
    void tearDown()
    {
        QuarkusTransaction.requiringNew().run(() -> {
            ModuleEntity detected = this.repository.findById(DETECTED);
            detected.enabled = true;
            detected.advertise(KubernetesSimulatedModule.TYPE);
            this.repository.deleteById(NEW);
        });
    }

    @Test
    void everyDetectedModuleIsRegisteredAtStartup()
    {
        assertTrue(this.repository.findById(DETECTED).enabled);
    }

    @Test
    void register_insertsTheNewModules_andLeavesTheStateOfTheExistingOnes()
    {
        QuarkusTransaction.requiringNew().run(() -> this.repository.findById(DETECTED).enabled = false);

        Set<String> registered = this.repository.register(List.of(KubernetesSimulatedModule.TYPE, NEW_TYPE));

        assertEquals(Set.of(NEW), registered);
        assertFalse(this.repository.findById(DETECTED).enabled);
        ModuleEntity created = this.repository.findById(NEW);
        assertTrue(created.enabled);
        assertNull(created.updatedBy);
        assertEquals(NEW_TYPE, created.type());
    }

    @Test
    void register_updatesTheAdvertisedTypeOfAnExistingModule()
    {
        AssetType changed = AssetType.of(KubernetesSimulatedModule.TYPE.namespace(), KubernetesSimulatedModule.TYPE.localName(), ActivityRegime.MEASURED, ServiceFamily.KUBERNETES_CONTAINER);

        this.repository.register(List.of(changed));
        assertEquals(ActivityRegime.MEASURED, this.repository.findById(DETECTED).regime);

        this.repository.register(List.of(KubernetesSimulatedModule.TYPE));
        assertEquals(KubernetesSimulatedModule.TYPE, this.repository.findById(DETECTED).type());
    }
}
