package tech.illuin.wombat.ui;

import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.context.model.UnrecognizedAsset;
import tech.illuin.wombat.context.persistence.AssetEntity;
import tech.illuin.wombat.context.persistence.AssetRepository;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.context.WombatContextProvider;
import tech.illuin.wombat.core.activity.commons.AssetFilter;
import tech.illuin.wombat.core.evaluation.WombatEvaluationException;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.evaluation.impact.commons.AssetImpact;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.impact.timeline.ImpactTimeline;
import tech.illuin.wombat.impact.timeline.ImpactTimelineResolver;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/")
public class UIController
{
    private final AssetEvaluator assetEvaluator;
    private final WombatContextProvider contextProvider;
    private final UIProperties uiProperties;
    private final AssetRepository assetRepository;
    private final ImpactTimelineResolver timelineResolver;

    private static final int DEFAULT_RANGE_MONTHS = 3;
    private static final Logger logger = LoggerFactory.getLogger(UIController.class);

    public UIController(AssetEvaluator assetEvaluator, WombatContextProvider contextProvider, UIProperties uiProperties, AssetRepository assetRepository, ImpactTimelineResolver timelineResolver)
    {
        this.assetEvaluator = assetEvaluator;
        this.contextProvider = contextProvider;
        this.uiProperties = uiProperties;
        this.assetRepository = assetRepository;
        this.timelineResolver = timelineResolver;
    }

    @GET @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get(
        @QueryParam("from") String from,
        @QueryParam("to") String to,
        @QueryParam("services") List<String> servicesParam,
        @QueryParam("environment") String environment
    ) {
        try {
            TimeRange timeRange = this.computeTimeRange(from, to);

            List<Environment> environments = this.contextProvider.provide().environments();
            if (environments.isEmpty())
                throw new IllegalStateException("No assets configured (check the monitored-environments YAML reconciled at startup)");

            List<Templates.EnvironmentView> environmentViews = environments.stream()
                .map(candidate -> new Templates.EnvironmentView(candidate.id(), candidate.id()))
                .toList();

            String selectedEnvironmentId = environment != null && environments.stream().anyMatch(candidate -> candidate.id().equals(environment))
                ? environment
                : environmentViews.getFirst().id();

            List<Asset> environmentAssets = environments.stream()
                .filter(candidate -> candidate.id().equals(selectedEnvironmentId))
                .flatMap(candidate -> candidate.assets().stream())
                .toList();

            List<UnrecognizedAsset> unrecognizedAssets = this.assetRepository.findByEnvironment(selectedEnvironmentId).stream()
                .map(AssetEntity::toProperties)
                .filter(UnrecognizedAsset.class::isInstance)
                .map(UnrecognizedAsset.class::cast)
                .toList();

            Map<String, List<String>> services = parseServices(servicesParam);

            List<String> effectiveAssetIds = services.keySet().stream()
                .filter(id -> environmentAssets.stream().anyMatch(asset -> asset.id().equals(id)))
                .toList();
            if (effectiveAssetIds.isEmpty())
                effectiveAssetIds = environmentAssets.stream().map(Asset::id).toList();

            List<String> selectedIds = effectiveAssetIds;

            List<AssetImpact> assetImpacts = this.assetEvaluator.evaluate(timeRange, AssetFilter.of(selectedEnvironmentId, selectedIds)).stream()
                .filter(AssetImpact.class::isInstance)
                .map(AssetImpact.class::cast)
                .toList();

            List<String> requestedServices = services.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();

            EnvironmentImpact environmentImpact = EnvironmentImpact.from(assetImpacts, environmentAssets, requestedServices, timeRange, unrecognizedAssets);
            ImpactTimeline timeline = this.timelineResolver.resolve(timeRange, assetImpacts, environmentImpact.includedServices());
            Templates.AssetSelection assetSelection = new Templates.AssetSelection(environmentAssets, effectiveAssetIds);
            Templates.EnvironmentSelection environmentSelection = new Templates.EnvironmentSelection(environmentViews, selectedEnvironmentId);
            return Templates.impact(environmentImpact, assetSelection, environmentSelection, this.maxSpan(), timeline);
        }
        catch (WombatEvaluationException e) {
            logger.warn("Failed to evaluate environment {}", environment, e);
            return Templates.impactError(this.computeTimeRange(from, to), this.maxSpan());
        }
    }

    private Templates.MaxSpan maxSpan()
    {
        String maxSpanLabel = this.uiProperties.maxDateRange().duration() + " " + this.uiProperties.maxDateRange().unit().name().toLowerCase();
        return new Templates.MaxSpan(this.uiProperties.maxDateRange().asDuration().toMillis(), maxSpanLabel);
    }

    private TimeRange computeTimeRange(String from, String to)
    {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss]").withZone(ZoneOffset.UTC);

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        Duration maxSpan = this.uiProperties.maxDateRange().asDuration();

        Instant end = ceilToDay(to != null && !to.isBlank()
            ? fmt.parse(to, Instant::from)
            : now.toInstant());
        Instant start = from != null && !from.isBlank()
            ? fmt.parse(from, Instant::from).truncatedTo(ChronoUnit.DAYS)
            : ZonedDateTime.ofInstant(end, ZoneOffset.UTC).minusMonths(DEFAULT_RANGE_MONTHS).toInstant();
        if (start.isAfter(end))
            start = end;
        if (Duration.between(start, end).compareTo(maxSpan) > 0)
            start = ceilToDay(end.minus(maxSpan));
        return new TimeRange(start, end);
    }

    private static Instant ceilToDay(Instant instant)
    {
        Instant day = instant.truncatedTo(ChronoUnit.DAYS);
        return day.equals(instant) ? instant : day.plus(1, ChronoUnit.DAYS);
    }

    private static Map<String, List<String>> parseServices(List<String> entries)
    {
        Map<String, List<String>> services = new LinkedHashMap<>();
        if (entries == null) return services;
        for (String entry : entries)
        {
            if (entry == null || entry.isBlank()) continue;
            int separator = entry.indexOf('=');
            String assetId = (separator < 0 ? entry : entry.substring(0, separator)).trim();
            if (assetId.isEmpty()) continue;
            List<String> assetServices = separator < 0
                ? List.of()
                : Arrays.stream(entry.substring(separator + 1).split(","))
                    .map(String::trim)
                    .filter(service -> !service.isEmpty())
                    .toList();
            services.put(assetId, assetServices);
        }
        return services;
    }
}
