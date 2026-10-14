package tech.illuin.wombat.ui;

import io.quarkus.runtime.annotations.RegisterForReflection;
import tech.illuin.wombat.engine.context.model.UnrecognizedAsset;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.asset.profile.ServerProvider;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.evaluation.impact.commons.Amount;
import tech.illuin.wombat.core.evaluation.impact.commons.AmountUnit;
import tech.illuin.wombat.core.evaluation.impact.commons.Footprint;
import tech.illuin.wombat.core.evaluation.impact.kubernetes.ClusterInfo;
import tech.illuin.wombat.core.evaluation.impact.kubernetes.ServerSpecification;
import tech.illuin.wombat.engine.impact.timeline.ImpactTimeline;
import tech.illuin.wombat.ui.breakdown.KubernetesAssetPanel;
import tech.illuin.wombat.ui.breakdown.LLMAssetPanel;

// Pebble resolves view-model properties at runtime, including in native executables.
@RegisterForReflection(targets = {
    CurrentUser.class, EnvironmentImpact.class, ServiceImpact.class,
    Templates.MaxSpan.class, Templates.AssetView.class,
    Templates.EnvironmentView.class, Templates.EnvironmentSelection.class,
    ImpactPageData.class, ImpactPageData.Service.class, ImpactPageData.Metric.class, ImpactPageData.Timeline.class,
    ImpactTimeline.class, ImpactTimeline.Series.class, ImpactTimeline.Bucket.class,
    KubernetesAssetPanel.class, KubernetesAssetPanel.Load.class, LLMAssetPanel.class,
    UnrecognizedAsset.class, TimeRange.class, AssetIdentity.class,
    AssetType.class, ActivityRegime.class, ServiceFamily.class, Enum.class,
    LLMProvider.class, ServerProvider.class, ClusterInfo.class, ServerSpecification.class,
    Amount.class, AmountUnit.class, Footprint.class,
    Footprint.FootprintImpact.class, Footprint.FootprintImpact.FootprintImpactItem.class
})
final class UITemplateReflection {}
