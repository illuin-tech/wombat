package tech.illuin.wombat.persistence.backend;

import io.quarkus.scheduler.Scheduler;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.persistence.backend.api.PersistenceBackend;
import tech.illuin.wombat.persistence.store.MetricStores;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class PersistenceEngineStoresTest
{
    private static final MetricStores DATABASE = new MetricStores(samples -> {}, samples -> {});
    private static final MetricStores PUSH = new MetricStores(samples -> {}, samples -> {});

    /* A worker mounts its database backend for the configuration tables, next to the push one its metrics go to. */
    private final PersistenceEngine engine = new PersistenceEngine(mock(Scheduler.class), List.of(
        PersistenceBackend.of("sqlite", List.of(), List.of(), DATABASE),
        PersistenceBackend.of("push", List.of(), List.of(), PUSH),
        PersistenceBackend.of("storeless", List.of(), List.of(), null),
        PersistenceBackend.disabled("postgresql")
    ));

    @Test
    void stores_areThoseOfTheSelectedBackend()
    {
        assertSame(DATABASE, this.engine.stores("sqlite"));
        assertSame(PUSH, this.engine.stores("push"));
    }

    @Test
    void stores_failForABackendThatIsNotMounted()
    {
        assertThrows(IllegalStateException.class, () -> this.engine.stores("postgresql"));
    }

    @Test
    void stores_failForABackendWithoutStores()
    {
        assertThrows(IllegalStateException.class, () -> this.engine.stores("storeless"));
    }
}
