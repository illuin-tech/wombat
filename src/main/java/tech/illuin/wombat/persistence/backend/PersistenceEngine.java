package tech.illuin.wombat.persistence.backend;

import io.quarkus.arc.All;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduler;
import io.vertx.core.impl.ConcurrentHashSet;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.persistence.backend.api.HookSupplier;
import tech.illuin.wombat.persistence.backend.api.HookPhase;
import tech.illuin.wombat.persistence.backend.api.ProcessSupplier;
import tech.illuin.wombat.persistence.backend.api.PersistenceBackend;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class PersistenceEngine implements AutoCloseable
{
    private final Map<String, PersistenceBackend> backends;
    private final Set<MountedHook> hooks;
    private final Set<MountedProcess> processes;
    private final Scheduler scheduler;

    private static final Logger logger = LoggerFactory.getLogger(PersistenceEngine.class);
    private static final int PRIORITY_RESOURCE_INIT = 900;
    private static final int PRIORITY_SETUP = 1000;

    @Inject
    public PersistenceEngine(Scheduler scheduler, @All List<PersistenceBackend> backends)
    {
        this.backends = new ConcurrentHashMap<>();
        this.hooks = new ConcurrentHashSet<>();
        this.processes = new ConcurrentHashSet<>();
        this.scheduler = scheduler;
        this.mount(backends);
    }

    public PersistenceEngine mount(Collection<PersistenceBackend> backends)
    {
        for (var backend : backends)
            mount(backend);
        return this;
    }

    public PersistenceEngine mount(PersistenceBackend backend)
    {
        if (!backend.enabled())
        {
            logger.info("Skipping disabled backend {}", backend.id());
            return this;
        }

        logger.info(
            "Mounting backend {} with hooks [{}] and processes [{}]",
            backend.id(),
            backend.hooks().stream().map(HookSupplier::id).collect(Collectors.joining(", ")),
            backend.processes().stream().map(ProcessSupplier::id).collect(Collectors.joining(", "))
        );
        backend.hooks().stream()
            .map(hook -> new MountedHook(backend.id(), hook))
            .forEach(this.hooks::add);
        backend.processes().stream()
            .map(process -> new MountedProcess(backend.id(), backend.id() + ":" + process.id(), process))
            .forEach(this.processes::add);
        this.scheduleProcesses(backend.id());
        this.backends.put(backend.id(), backend);

        return this;
    }

    public PersistenceEngine unmount(PersistenceBackend backend)
    {
        logger.info("Unmounting backend {}", backend.id());
        this.hooks.removeIf(mounted -> mounted.backendId().equals(backend.id()));
        this.unscheduleProcesses(backend.id());
        this.processes.removeIf(mounted -> mounted.backendId().equals(backend.id()));
        this.backends.remove(backend.id());

        return this;
    }

    public Set<String> mountedBackends()
    {
        return this.backends.keySet();
    }

    public boolean isMounted(String backendId)
    {
        return this.backends.containsKey(backendId);
    }

    public void onStartupResourceInit(@Observes @Priority(PRIORITY_RESOURCE_INIT) StartupEvent event)
    {
        this.runHooks(HookPhase.RESOURCE_INIT);
    }

    public void onStartupSetup(@Observes @Priority(PRIORITY_SETUP) StartupEvent event)
    {
        this.runHooks(HookPhase.BACKEND_SETUP);
    }

    public void onShutdown(@Observes ShutdownEvent event)
    {
        this.runHooks(HookPhase.BACKEND_TEARDOWN);
    }

    private void runHooks(HookPhase phase)
    {
        logger.info("Running persistence engine {} phase", phase.name());
        this.hooks.stream()
            .filter(mounted -> mounted.hook().phase() == phase)
            .sorted(Comparator.comparingInt(mounted -> mounted.hook().priority()))
            .forEach(mounted -> {
                logger.debug("Running persistence engine {} hook {}", mounted.backendId(), mounted.hook().id());
                mounted.hook().supply().run();
            });
    }

    private void scheduleProcesses(String backendId)
    {
        logger.info("Registering scheduled processes for backend {}", backendId);
        this.processes.stream()
            .filter(mounted -> mounted.backendId().equals(backendId))
            .forEach(mounted -> this.scheduler.newJob(mounted.jobId())
                .setCron(mounted.process().cron())
                .setDelayed("PT15S")
                .setTask(_ -> {
                    logger.info("Running persistence engine {} process {}", backendId, mounted.process().id());
                    mounted.process().supply().run();
                })
                .schedule()
            );
    }

    private void unscheduleProcesses(String backendId)
    {
        logger.info("Unregistering scheduled processes for backend {}", backendId);
        this.processes.stream()
            .filter(mounted -> mounted.backendId().equals(backendId))
            .forEach(mounted -> this.scheduler.unscheduleJob(mounted.jobId()));
    }

    @Override @PreDestroy
    public void close() throws Exception
    {
        for (PersistenceBackend backend : this.backends.values())
        {
            if (backend instanceof AutoCloseable closeableBackend)
                closeableBackend.close();
        }
    }

    private record MountedHook(String backendId, HookSupplier hook) {}

    private record MountedProcess(String backendId, String jobId, ProcessSupplier process) {}
}
