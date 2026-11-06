package tech.illuin.wombat.persistence.backend.push;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.source.data.MetricData;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.persistence.backend.api.store.MetricSample;
import tech.illuin.wombat.persistence.backend.api.store.MetricStore;
import tech.illuin.wombat.persistence.backend.api.store.SkippedMetrics;

import java.util.Collection;
import java.util.List;

public class PushMetricStore<T extends MetricData> implements MetricStore<T>
{
    private final WombatPushClient client;
    private final String worker;
    private final ObjectMapper mapper;

    private static final Logger logger = LoggerFactory.getLogger(PushMetricStore.class);
    private static final TypeReference<List<SkippedMetrics>> SKIPPED = new TypeReference<>() {};

    public PushMetricStore(WombatPushClient client, String worker, ObjectMapper mapper)
    {
        this.client = client;
        this.worker = worker;
        this.mapper = mapper;
    }

    @Override
    public void save(Collection<MetricSample<T>> samples)
    {
        if (samples.isEmpty())
            return;

        List<MetricSample<MetricData>> pushed = samples.stream()
            .map(sample -> new MetricSample<MetricData>(sample.asset(), sample.window(), sample.data()))
            .toList();
        Response<Integer> response = this.client.push(this.worker, pushed);
        logger.debug("Pushed {} sample(s)", pushed.size());

        for (SkippedMetrics skipped : this.skipped(response))
            logger.warn("The aggregator skipped {} metric(s) of asset {} ({}): {}", skipped.count(), skipped.assetId(), skipped.assetType(), skipped.reason());
    }

    private List<SkippedMetrics> skipped(Response<Integer> response)
    {
        if (response == null || response.status() == null || response.status().metadata() == null)
            return List.of();
        Object errors = response.status().metadata().get(SkippedMetrics.STATUS_KEY);
        return errors == null ? List.of() : this.mapper.convertValue(errors, SKIPPED);
    }
}
