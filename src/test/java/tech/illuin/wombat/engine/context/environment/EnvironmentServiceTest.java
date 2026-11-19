package tech.illuin.wombat.engine.context.environment;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentEntity;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentRepository;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnvironmentServiceTest
{
    private final EnvironmentRepository environments = mock(EnvironmentRepository.class);
    private final AssetRepository assets = mock(AssetRepository.class);
    private final EnvironmentService service = new EnvironmentService(this.environments, this.assets);

    @Test
    void create_persistsANewEnvironmentCreatedThroughTheApi()
    {
        EnvironmentEntity created = this.service.create("prod", "Production", "Main", true);

        verify(this.environments).persist(created);
        assertEquals("prod", created.id);
        assertEquals("Production", created.name);
        assertEquals("Main", created.description);
        assertFalse(created.provisioned);
        assertNotNull(created.disabledAt);
    }

    @Test
    void create_failsOnAnExistingEnvironment_provisionedOrNot()
    {
        when(this.environments.findByIdOptional("prod")).thenReturn(Optional.of(environment("prod", false)));
        when(this.environments.findByIdOptional("test")).thenReturn(Optional.of(environment("test", true)));

        assertThrows(EnvironmentConflictException.class, () -> this.service.create("prod", "Production", null, false));
        assertThrows(EnvironmentConflictException.class, () -> this.service.create("test", "Test", null, false));
    }

    @Test
    void create_takesOverASoftDeletedEnvironment()
    {
        EnvironmentEntity deleted = environment("prod", true);
        deleted.uuid = "kept";
        deleted.deletedAt = Instant.now();
        when(this.environments.findByIdOptional("prod")).thenReturn(Optional.of(deleted));

        EnvironmentEntity created = this.service.create("prod", "Production", null, false);

        assertSame(deleted, created);
        assertEquals("kept", created.uuid);
        assertNull(created.deletedAt);
        assertFalse(created.provisioned);
        verify(this.environments, never()).persist(created);
    }

    @Test
    void get_ignoresUnknownAndSoftDeletedEnvironments()
    {
        assertThrows(UnknownEnvironmentException.class, () -> this.service.get("prod"));
        assertThrows(UnknownEnvironmentException.class, () -> this.service.getByUuid("uuid"));
    }

    @Test
    void update_replacesTheEditableFields()
    {
        EnvironmentEntity entity = environment("prod", false);
        when(this.environments.findVisibleById("prod")).thenReturn(Optional.of(entity));

        this.service.update("prod", "Renamed", "Described", true);

        assertEquals("Renamed", entity.name);
        assertEquals("Described", entity.description);
        assertNotNull(entity.disabledAt);
    }

    @Test
    void update_keepsWhenItWasDisabled_andReenables()
    {
        Instant disabledAt = Instant.parse("2026-01-01T00:00:00Z");
        EnvironmentEntity entity = environment("prod", false);
        entity.disabledAt = disabledAt;
        when(this.environments.findVisibleById("prod")).thenReturn(Optional.of(entity));

        this.service.update("prod", "Production", null, true);
        assertEquals(disabledAt, entity.disabledAt);

        this.service.update("prod", "Production", null, false);
        assertNull(entity.disabledAt);
    }

    @Test
    void updateAndDelete_failOnAProvisionedEnvironment()
    {
        when(this.environments.findVisibleById("test")).thenReturn(Optional.of(environment("test", true)));

        assertThrows(EnvironmentConflictException.class, () -> this.service.update("test", "Test", null, false));
        assertThrows(EnvironmentConflictException.class, () -> this.service.delete("test"));
    }

    @Test
    void delete_softDeletesAnEmptyEnvironment()
    {
        EnvironmentEntity entity = environment("prod", false);
        when(this.environments.findVisibleById("prod")).thenReturn(Optional.of(entity));

        this.service.delete("prod");

        assertNotNull(entity.deletedAt);
    }

    @Test
    void delete_failsOnAnEnvironmentHoldingAssets()
    {
        EnvironmentEntity entity = environment("prod", false);
        when(this.environments.findVisibleById("prod")).thenReturn(Optional.of(entity));
        when(this.assets.countVisibleByEnvironment("prod")).thenReturn(1L);

        assertThrows(EnvironmentConflictException.class, () -> this.service.delete("prod"));
        assertNull(entity.deletedAt);
    }

    private static EnvironmentEntity environment(String id, boolean provisioned)
    {
        EnvironmentEntity entity = new EnvironmentEntity();
        entity.id = id;
        entity.name = id;
        entity.provisioned = provisioned;
        return entity;
    }
}
