package tech.illuin.wombat.persistence.backend.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.source.data.MetricData;
import tech.illuin.wombat.persistence.push.MetricPush;
import tech.illuin.wombat.persistence.store.MetricSample;
import tech.illuin.wombat.persistence.store.MetricStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PushMetricStore<T extends MetricData> implements MetricStore<T>
{
    private final WombatPushClient client;

    private static final Logger logger = LoggerFactory.getLogger(PushMetricStore.class);

    public PushMetricStore(WombatPushClient client)
    {
        this.client = client;
    }

    @Override
    public void save(Collection<MetricSample<T>> samples)
    {
        if (samples.isEmpty())
            return;

        Map<AssetKey, List<MetricData>> byAsset = new LinkedHashMap<>();
        for (MetricSample<T> sample : samples)
            byAsset.computeIfAbsent(new AssetKey(sample.asset(), sample.assetType()), key -> new ArrayList<>()).add(sample.data());

        List<MetricPush> pushes = byAsset.entrySet().stream()
            .map(entry -> new MetricPush(entry.getKey().asset(), entry.getKey().assetType(), entry.getValue()))
            .toList();
        this.client.push(pushes);
        logger.debug("Pushed {} sample(s) for {} asset(s)", samples.size(), pushes.size());
    }

    private record AssetKey(AssetIdentity asset, String assetType) {}
}
