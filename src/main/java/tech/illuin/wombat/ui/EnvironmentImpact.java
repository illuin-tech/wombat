package tech.illuin.wombat.ui;

import tech.illuin.wombat.context.model.UnrecognizedAsset;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.asset.profile.LLMProfile;
import tech.illuin.wombat.core.asset.profile.ServerProfile;
import tech.illuin.wombat.core.connector.ecologits.connector.model.EcologitsEstimationResponse;
import tech.illuin.wombat.core.evaluation.impact.commons.Amount;
import tech.illuin.wombat.core.evaluation.impact.commons.AmountUnit;
import tech.illuin.wombat.core.evaluation.impact.commons.AssetImpact;
import tech.illuin.wombat.core.evaluation.impact.kubernetes.ClusterInfo;
import tech.illuin.wombat.core.evaluation.impact.commons.Footprint;
import tech.illuin.wombat.core.evaluation.impact.kubernetes.KubernetesImpact;
import tech.illuin.wombat.core.evaluation.impact.llm.LLMImpact;
import tech.illuin.wombat.core.evaluation.impact.kubernetes.ServerSpecification;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.module.llm_static.LLMStaticProfile;
import tech.illuin.wombat.ui.breakdown.AssetPanel;
import tech.illuin.wombat.ui.breakdown.KubernetesAssetPanel;
import tech.illuin.wombat.ui.breakdown.LLMAssetPanel;
import tech.illuin.wombat.ui.breakdown.ServiceLine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record EnvironmentImpact(
    Footprint globalImpact,
    Footprint dockerImpact,
    Footprint llmImpact,
    List<ServiceImpact> serviceImpacts,
    Map<String, Double> impactShares,
    List<String> allServices,
    List<String> includedServices,
    List<AssetPanel> assetPanels,
    List<ServiceLine> serviceLines,
    TimeRange timeRange,
    List<UnrecognizedAsset> unrecognizedAssets
) {
    /**
     * Folds the per-asset impacts produced by the SDK's impact-calculator into the single view model the impact page
     * renders.
     * <p>
     * {@code assetImpacts} is expected to be unfiltered: the full set of services is what feeds the service picker,
     * and {@code requestedServices} then narrows what the cards, the panels and the table account for. An empty
     * {@code requestedServices} means "every service".
     */
    public static EnvironmentImpact from(
        List<AssetImpact> assetImpacts,
        List<Asset> assets,
        Collection<String> requestedServices,
        TimeRange timeRange
    ) {
        return from(assetImpacts, assets, requestedServices, timeRange, List.of());
    }

    public static EnvironmentImpact from(
        List<AssetImpact> assetImpacts,
        List<Asset> assets,
        Collection<String> requestedServices,
        TimeRange timeRange,
        List<UnrecognizedAsset> unrecognizedAssets
    ) {
        Map<String, String> assetNames = new LinkedHashMap<>();
        assets.forEach(asset -> assetNames.putIfAbsent(asset.identity().id(), asset.identity().name()));

        List<String> allServices = assetImpacts.stream()
            .flatMap(impact -> impact.serviceImpacts().stream())
            .map(tech.illuin.wombat.core.evaluation.impact.commons.ServiceImpact::serviceId)
            .distinct()
            .sorted()
            .toList();
        Set<String> included = new LinkedHashSet<>(requestedServices == null || requestedServices.isEmpty()
            ? allServices
            : allServices.stream().filter(requestedServices::contains).toList());

        List<AssetPanel> panels = new ArrayList<>();
        List<ServiceImpact> services = new ArrayList<>();
        List<Footprint> dockerFootprints = new ArrayList<>();
        List<Footprint> llmFootprints = new ArrayList<>();

        for (AssetImpact assetImpact : assetImpacts)
        {
            String name = assetNames.getOrDefault(assetImpact.assetId(), assetImpact.assetId());
            List<tech.illuin.wombat.core.evaluation.impact.commons.ServiceImpact> includedImpacts = assetImpact.serviceImpacts().stream()
                .filter(impact -> included.contains(impact.serviceId()))
                .toList();
            if (includedImpacts.isEmpty())
                continue;

            includedImpacts.forEach(impact -> services.add(ServiceImpact.of(name, impact)));
            Footprint assetFootprint = Footprint.sum(includedImpacts.stream()
                .map(tech.illuin.wombat.core.evaluation.impact.commons.ServiceImpact::footprint)
                .toList());

            List<KubernetesImpact> kubernetesImpacts = includedImpacts.stream()
                .filter(KubernetesImpact.class::isInstance)
                .map(KubernetesImpact.class::cast)
                .toList();
            if (!kubernetesImpacts.isEmpty())
            {
                dockerFootprints.add(assetFootprint);
                panels.add(kubernetesPanel(name, assetFootprint, kubernetesImpacts));
            }

            List<LLMImpact> llmImpacts = includedImpacts.stream()
                .filter(LLMImpact.class::isInstance)
                .map(LLMImpact.class::cast)
                .toList();
            if (!llmImpacts.isEmpty())
            {
                llmFootprints.add(assetFootprint);
                panels.add(llmPanel(name, llmImpacts.getFirst()));
            }
        }

        services.sort(Comparator.comparingDouble((ServiceImpact service) -> service.footprint().gwp().totalValue()).reversed());

        double totalGwp = services.stream().mapToDouble(service -> service.footprint().gwp().totalValue()).sum();
        Map<String, Double> shares = new LinkedHashMap<>();
        services.forEach(service ->
            shares.put(service.label(), totalGwp == 0.0 ? 0.0 : service.footprint().gwp().totalValue() / totalGwp)
        );

        List<Footprint> globalFootprints = new ArrayList<>(dockerFootprints);
        globalFootprints.addAll(llmFootprints);

        return new EnvironmentImpact(
            Footprint.sum(globalFootprints),
            Footprint.sum(dockerFootprints),
            Footprint.sum(llmFootprints),
            services,
            shares,
            allServices,
            List.copyOf(included),
            panels,
            serviceLines(services),
            timeRange,
            unrecognizedAssets != null ? unrecognizedAssets : List.of()
        );
    }

    public List<KubernetesAssetPanel> kubernetesPanels()
    {
        return this.assetPanels.stream()
            .filter(KubernetesAssetPanel.class::isInstance)
            .map(KubernetesAssetPanel.class::cast)
            .toList();
    }

    public List<LLMAssetPanel> llmPanels()
    {
        return this.assetPanels.stream()
            .filter(LLMAssetPanel.class::isInstance)
            .map(LLMAssetPanel.class::cast)
            .toList();
    }

    public int assetCount()
    {
        return this.assetPanels.size();
    }

    public int serviceCount()
    {
        return this.serviceImpacts.size();
    }

    public long dockerServiceCount()
    {
        return this.serviceImpacts.stream().filter(s -> s.assetType().family() == ServiceFamily.KUBERNETES_CONTAINER).count();
    }

    public long llmServiceCount()
    {
        return this.serviceImpacts.stream().filter(s -> s.assetType().family() == ServiceFamily.LLM).count();
    }

    private static AssetPanel kubernetesPanel(String name, Footprint footprint, List<KubernetesImpact> impacts)
    {
        KubernetesImpact first = impacts.getFirst();
        ServerProfile profile = first.profile();
        ServerSpecification specification = first.serverSpecification();
        List<ClusterInfo> clusters = impacts.stream()
            .map(KubernetesImpact::location)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        return new KubernetesAssetPanel(
            name,
            first.assetType(),
            first.assetType().regime(),
            impacts.stream().map(KubernetesImpact::serviceId).toList(),
            footprintAmount(footprint.gwp(), AmountUnit.kg_co2eq),
            // Boavizta reports no energy metric for a server impact; only the LLM panel renders an energy total.
            new Amount(0.0, AmountUnit.kwh),
            footprintAmount(footprint.pe(), AmountUnit.mj),
            footprintAmount(footprint.adp(), AmountUnit.kg_sbeq),
            profile.provider(),
            profile.instanceType(),
            profile.location(),
            profile.lifespan(),
            clusters,
            specification,
            new KubernetesAssetPanel.Load(cpuUsageCores(first, specification), first.loadPercent(), first.nodeCount())
        );
    }

    private static AssetPanel llmPanel(String name, LLMImpact impact)
    {
        LLMProfile profile = impact.profile();
        EcologitsEstimationResponse.Impacts impacts = impact.estimation().impacts();
        int requestPerYear = profile instanceof LLMStaticProfile staticProfile
            ? staticProfile.requestProfile().requestPerYear()
            : 0;

        return new LLMAssetPanel(
            name,
            impact.assetType(),
            impact.assetType().regime(),
            List.of(impact.serviceId()),
            metricAmount(impacts.gwp(), impact.requestCount(), AmountUnit.kg_co2eq),
            metricAmount(impacts.energy(), impact.requestCount(), AmountUnit.kwh),
            metricAmount(impacts.pe(), impact.requestCount(), AmountUnit.mj),
            metricAmount(impacts.adpe(), impact.requestCount(), AmountUnit.kg_sbeq),
            metricAmount(impacts.gwp(), 1.0, AmountUnit.kg_co2eq),
            profile.provider(),
            profile.model(),
            profile.location(),
            impact.outputTokenCount(),
            requestPerYear,
            impact.requestCount()
        );
    }

    private static List<ServiceLine> serviceLines(List<ServiceImpact> services)
    {
        return services.stream()
            .map(service -> new ServiceLine(
                service.service(),
                service.assetType().regime(),
                service.assetType().family(),
                footprintAmount(service.footprint().gwp(), AmountUnit.kg_co2eq),
                new Amount(0.0, AmountUnit.kwh),
                footprintAmount(service.footprint().pe(), AmountUnit.mj),
                footprintAmount(service.footprint().adp(), AmountUnit.kg_sbeq)
            ))
            .toList();
    }

    /**
     * The load formula in {@code BoaviztaLoadImpactResolver} is {@code loadPercent = cores / vcpu * 100}, so the
     * cores the page shows are recovered by inverting it rather than carried a second time on the impact.
     */
    private static double cpuUsageCores(KubernetesImpact impact, ServerSpecification specification)
    {
        int vcpu = specification != null && specification.vcpu() != null ? specification.vcpu() : 0;
        return vcpu > 0 ? impact.loadPercent() / 100.0 * vcpu : 0.0;
    }

    private static Amount footprintAmount(Footprint.FootprintImpact impact, AmountUnit fallback)
    {
        if (impact == null)
            return new Amount(0.0, fallback);
        return new Amount(impact.totalValue(), AmountUnit.forSymbol(impact.unit()).orElse(fallback));
    }

    private static Amount metricAmount(EcologitsEstimationResponse.Metric metric, double factor, AmountUnit fallback)
    {
        if (metric == null)
            return new Amount(0.0, fallback);
        AmountUnit unit = AmountUnit.forSymbol(metric.unit()).orElse(fallback);
        return new Amount(EcologitsEstimationResponse.Metric.mean(metric) * factor, unit);
    }
}
