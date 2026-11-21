package tech.illuin.wombat.engine.simulation.evaluation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.illuin.wombat.core.evaluation.AssetEvaluation;
import tech.illuin.wombat.core.evaluation.impact.commons.Amount;
import tech.illuin.wombat.core.evaluation.impact.commons.Footprint;
import tech.illuin.wombat.engine.simulation.SimulationPeriod;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What a simulation would cost and emit: its assets evaluated over the reference {@code window}, each result then
 * scaled linearly to every period.
 *
 * @param totals the scaled results summed over the assets evaluated {@link AssetStatus#OK}, per period
 */
public record SimulationEvaluation(
    Window window,
    Map<SimulationPeriod, Scaled> totals,
    List<AssetResult> assets
) {
    public record Window(Instant start, Instant end, SimulationPeriod period) {}

    /**
     * @param impact null when the impact is not evaluated
     * @param cost   null when the cost is not evaluated, and always for now: a cost carries no amount yet
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Scaled(Footprint impact, Amount cost) {}

    /**
     * @param message  why the asset produced nothing, for any status but {@link AssetStatus#OK}
     * @param coverage the share of the window's slots with activity, from 0 to 1; null unless {@link AssetStatus#OK}
     * @param result   the evaluations straight from the evaluator, over the window
     * @param scaled   the standardized part of {@code result}, scaled to each period
     */
    public record AssetResult(
        String id,
        AssetStatus status,
        String message,
        Double coverage,
        List<AssetEvaluation> result,
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
