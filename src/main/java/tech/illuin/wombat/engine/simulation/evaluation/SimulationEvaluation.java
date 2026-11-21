package tech.illuin.wombat.engine.simulation.evaluation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.evaluation.AssetEvaluation;
import tech.illuin.wombat.core.evaluation.cost.commons.Money;
import tech.illuin.wombat.core.evaluation.impact.commons.Footprint;
import tech.illuin.wombat.engine.simulation.SimulationPeriod;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What a simulation would cost and emit: its assets evaluated over the reference {@code window}, each result then
 * scaled linearly to every period. Every scaled figure is served, so that a client never scales anything itself.
 *
 * @param factors how many windows each period holds: a period's figures are the window's times its factor
 * @param totals  the scaled results summed over the assets evaluated {@link AssetStatus#OK}, per period
 */
public record SimulationEvaluation(
    Window window,
    Map<SimulationPeriod, Double> factors,
    Map<SimulationPeriod, Scaled> totals,
    List<AssetResult> assets
) {
    public record Window(Instant start, Instant end, SimulationPeriod period) {}

    /**
     * The standardized part of results, scaled to one period.
     *
     * @param impact   null when the impact is not evaluated
     * @param cost     one amount per currency, never converted; null when the cost is not evaluated, and empty when
     *                 nothing was priced
     * @param services the impact and cost per service, null when neither is evaluated; only an asset's results have them
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Scaled(
        Footprint impact,
        List<Money> cost,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ScaledService> services
    ) {}

    /**
     * @param impact null when the impact is not evaluated, or the service has none
     * @param cost   as {@link Scaled#cost()}
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ScaledService(String serviceId, AssetType assetType, Footprint impact, List<Money> cost)
    {
        ScaledService multiply(double factor)
        {
            return new ScaledService(
                this.serviceId,
                this.assetType,
                this.impact != null ? this.impact.multiply(factor) : null,
                multiply(this.cost, factor)
            );
        }

        static List<Money> multiply(List<Money> cost, double factor)
        {
            return cost != null ? cost.stream().map(amount -> amount.multiply(factor)).toList() : null;
        }
    }

    /**
     * @param message  why the asset produced nothing, for any status but {@link AssetStatus#OK}
     * @param coverage the share of the window's slots with activity, from 0 to 1; null unless {@link AssetStatus#OK}
     * @param result   the evaluations straight from the evaluator, over the window and unscaled; the asset is the one
     *                 named by {@code id}, so their identity is left out
     * @param scaled   the standardized part of {@code result}, scaled to each period
     */
    public record AssetResult(
        String id,
        AssetStatus status,
        String message,
        Double coverage,
        @JsonIgnoreProperties({"assetIdentity"}) List<AssetEvaluation> result,
        Map<SimulationPeriod, Scaled> scaled
    ) {}

    public enum AssetStatus
    {
        @JsonProperty("ok") OK,
        /** The asset's resolvers found no activity in the window. */
        @JsonProperty("no-data") NO_DATA,
        /** The running core has no module for the asset's type. */
        @JsonProperty("module-disabled") MODULE_DISABLED,
        @JsonProperty("error") ERROR,
    }
}
