package tech.illuin.wombat.persistence.backend.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import feign.Client;
import feign.Feign;
import feign.Request;
import feign.jackson.JacksonEncoder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.illuin.wombat.commons.NodeMode;
import tech.illuin.wombat.commons.NodeModeConfigSourceFactory;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.engine.monitor.MonitoredEnvironments;
import tech.illuin.wombat.persistence.backend.api.Action;
import tech.illuin.wombat.persistence.backend.api.HookSupplier;
import tech.illuin.wombat.persistence.backend.api.PersistenceBackend;
import tech.illuin.wombat.persistence.backend.api.ProcessSupplier;
import tech.illuin.wombat.persistence.store.MetricStores;
import tech.illuin.wombat.security.apikey.ApiKeyAuthenticationMechanism;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static tech.illuin.wombat.persistence.backend.api.HookPhase.BACKEND_SETUP;

/**
 * Backend of the {@link NodeMode#WORKER} mode: sampled metrics are pushed to an aggregator instead of the local
 * database, and the environments they belong to are declared to it.
 */
@ApplicationScoped
public class PushBackendConfig
{
    public static final String BACKEND_PUSH = "push";
    public static final String WORKER_ID_PATTERN = "[A-Za-z0-9._-]+";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String DECLARATION_CRON = "0 * * * * ?";

    @Produces @Singleton
    public PersistenceBackend providePushBackend(
        WombatProperties wombat,
        @ConfigProperty(name = "quarkus.datasource.db-kind") String dbKind,
        Instance<ObjectMapper> mapper,
        Instance<JsonMapper> assetMapper,
        Instance<MonitoredEnvironments> environments
    ) {
        if (wombat.mode() != NodeMode.WORKER)
            return PersistenceBackend.disabled(BACKEND_PUSH);

        WorkerProperties properties = wombat.worker();
        AggregatorProperties aggregator = properties.aggregator().orElseThrow(() -> new IllegalArgumentException("wombat.worker.aggregator properties must be provided in worker mode"));
        String worker = properties.id().orElseThrow(() -> new IllegalArgumentException("wombat.worker.id must be provided in worker mode"));
        if (!worker.matches(WORKER_ID_PATTERN))
            throw new IllegalArgumentException("wombat.worker.id must match " + WORKER_ID_PATTERN + ", got: " + worker);
        if (!dbKind.equals(NodeModeConfigSourceFactory.WORKER_DB_KIND))
            throw new IllegalStateException("Worker mode only runs on SQLite, got quarkus.datasource.db-kind: " + dbKind);

        Supplier<Action> declaration = () -> new EnvironmentDeclaration(
            createClient(WombatEnvironmentClient.class, aggregator, assetMapper.get()), worker, environments.get()
        );
        return PersistenceBackend.of(
            BACKEND_PUSH,
            List.of(new HookSupplier("environment-declaration", BACKEND_SETUP, declaration)),
            List.of(new ProcessSupplier("environment-declaration", DECLARATION_CRON, declaration)),
            stores(aggregator, mapper.get())
        );
    }

    public static MetricStores stores(AggregatorProperties properties, ObjectMapper mapper)
    {
        WombatPushClient client = createClient(WombatPushClient.class, properties, mapper);
        return new MetricStores(
            new PushMetricStore<>(client),
            new PushMetricStore<>(client)
        );
    }

    public static <T> T createClient(Class<T> type, AggregatorProperties properties, ObjectMapper mapper)
    {
        Duration timeout = properties.timeout().orElse(DEFAULT_TIMEOUT);
        Feign.Builder builder = Feign.builder()
            .encoder(new JacksonEncoder(mapper))
            .requestInterceptor(template -> template.header(ApiKeyAuthenticationMechanism.HEADER, properties.apiKey()))
            .options(new Request.Options(timeout, timeout, true));

        if (properties.proxyUrl().isPresent() && !properties.proxyUrl().get().isBlank())
        {
            URI proxy = URI.create(properties.proxyUrl().get());
            if (proxy.getHost() == null || proxy.getPort() == -1)
                throw new IllegalArgumentException("wombat.worker.aggregator.proxy-url must include a host and port, got: " + properties.proxyUrl().get());
            builder.client(new Client.Proxied(null, null, new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy.getHost(), proxy.getPort()))));
        }

        return builder.target(type, properties.url().replaceAll("/+$", ""));
    }
}
