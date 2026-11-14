package tech.illuin.wombat.persistence.backend.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import feign.Client;
import feign.Feign;
import feign.Request;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import tech.illuin.wombat.commons.NodeMode;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.engine.monitor.MonitoredEnvironments;
import tech.illuin.wombat.persistence.backend.api.Action;
import tech.illuin.wombat.persistence.backend.api.HookSupplier;
import tech.illuin.wombat.persistence.backend.api.PersistenceBackend;
import tech.illuin.wombat.persistence.backend.api.ProcessSupplier;
import tech.illuin.wombat.persistence.backend.api.store.MetricStoreContainer;
import tech.illuin.wombat.security.apikey.ApiKeyAuthenticationMechanism;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
    /** No {@code .}, which ends the worker id in the ids of its assets on the aggregator, {@code workerId.assetId}. */
    public static final String WORKER_ID_PATTERN = "[a-z0-9_-]+";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String DECLARATION_CRON = "0 * * * * ?";

    @Produces @Singleton
    public PersistenceBackend providePushBackend(
        WombatProperties wombat,
        Instance<ObjectMapper> mapper,
        Instance<JsonMapper> assetMapper,
        Instance<MonitoredEnvironments> environments
    ) {
        if (wombat.mode() != NodeMode.worker)
            return PersistenceBackend.disabled(BACKEND_PUSH);

        WorkerProperties properties = wombat.worker();
        AggregatorProperties aggregator = properties.aggregator().orElseThrow(() -> new IllegalArgumentException("wombat.worker.aggregator properties must be provided in worker mode"));
        String worker = properties.id().orElseThrow(() -> new IllegalArgumentException("wombat.worker.id must be provided in worker mode"));
        if (!worker.matches(WORKER_ID_PATTERN))
            throw new IllegalArgumentException("wombat.worker.id must match " + WORKER_ID_PATTERN + ", got: " + worker);

        Supplier<Action> declaration = () -> new EnvironmentDeclaration(
            createClient(WombatEnvironmentClient.class, aggregator, assetMapper.get()), worker, environments.get()
        );
        return PersistenceBackend.of(
            BACKEND_PUSH,
            List.of(new HookSupplier("environment-declaration", BACKEND_SETUP, declaration)),
            List.of(new ProcessSupplier("environment-declaration", DECLARATION_CRON, declaration)),
            stores(aggregator, worker, mapper.get())
        );
    }

    public static MetricStoreContainer stores(AggregatorProperties properties, String worker, ObjectMapper mapper)
    {
        WombatPushClient client = createClient(WombatPushClient.class, properties, mapper);
        return new MetricStoreContainer(Map.of(
            ServiceFamily.KUBERNETES_CONTAINER, new PushMetricStore<>(client, worker, mapper),
            ServiceFamily.LLM, new PushMetricStore<>(client, worker, mapper)
        ));
    }

    public static <T> T createClient(Class<T> type, AggregatorProperties properties, ObjectMapper mapper)
    {
        Duration timeout = properties.timeout().orElse(DEFAULT_TIMEOUT);
        Feign.Builder builder = Feign.builder()
            .encoder(new JacksonEncoder(mapper))
            .decoder(new JacksonDecoder(mapper))
            .requestInterceptor(template -> template.header(ApiKeyAuthenticationMechanism.HEADER, properties.apiKey()))
            .options(new Request.Options(timeout, timeout, true));

        properties.proxyUrl().ifPresent(proxyUrl -> {
            URI proxy = URI.create(proxyUrl);
            if (proxy.getHost() == null || proxy.getPort() == -1)
                throw new IllegalArgumentException("wombat.worker.aggregator.proxy-url must include a host and port, got: " + proxyUrl);
            builder.client(new Client.Proxied(null, null, new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy.getHost(), proxy.getPort()))));
        });

        return builder.target(type, properties.url().replaceAll("/+$", ""));
    }
}
