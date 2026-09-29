package tech.illuin.wombat.ui;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.profile.AssetProfile;
import tech.illuin.wombat.core.asset.profile.LLMProfile;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.connector.ecologits.connector.model.EcologitsEstimationResponse;
import tech.illuin.wombat.core.evaluation.impact.commons.AssetImpact;
import tech.illuin.wombat.core.evaluation.impact.commons.Footprint;
import tech.illuin.wombat.core.evaluation.impact.commons.ImpactProvider;
import tech.illuin.wombat.core.evaluation.impact.llm.LLMImpact;
import tech.illuin.wombat.ui.breakdown.LLMAssetPanel;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentImpactTest
{
    private static final AssetType LLM_TYPE = AssetType.of("tech.illuin.wombat-module.llm-static", "llm-static", ActivityRegime.MODELED, ServiceFamily.LLM);
    private static final TimeRange RANGE = new TimeRange(Instant.ofEpochMilli(0), Instant.ofEpochMilli(1000));

    @Test
    void of_multiServiceLLMAsset_consolidatesServicesAndSumsTotals()
    {
        LLMProfile profile = new LLMProfile() {};

        Footprint fp1 = createFootprint(1.5f, 3.0f, 0.1f);
        Footprint fp2 = createFootprint(0.5f, 1.0f, 0.05f);

        LLMImpact impact1 = new LLMImpact(
            "mistral-large-latest",
            LLM_TYPE,
            profile,
            LLMProvider.mistralai,
            "mistral-large-latest",
            "FRA",
            0.75,
            fp1,
            createEstimationResponse(1.5, 3.0, 0.1, 0.02),
            1500L,
            10.0
        );

        LLMImpact impact2 = new LLMImpact(
            "mistral-small-latest",
            LLM_TYPE,
            profile,
            LLMProvider.mistralai,
            "mistral-small-latest",
            "FRA",
            0.25,
            fp2,
            createEstimationResponse(0.5, 1.0, 0.05, 0.01),
            500L,
            20.0
        );

        Footprint globalFootprint = Footprint.sum(List.of(fp1, fp2));
        AssetImpact assetImpact = new AssetImpact(
            "prod",
            "llm-asset-1",
            globalFootprint,
            List.of(impact1, impact2),
            ImpactProvider.ECOLOGITS
        );

        TestAsset asset = new TestAsset(AssetIdentity.of("llm-asset-1", "prod", "Test LLM Asset"), LLM_TYPE, profile);
        EnvironmentImpact envImpact = EnvironmentImpact.from(List.of(assetImpact), List.of(asset), List.of(), RANGE);
        assertEquals(1, envImpact.llmPanels().size());

        LLMAssetPanel panel = envImpact.llmPanels().getFirst();
        assertEquals(List.of("mistral-large-latest", "mistral-small-latest"), panel.serviceIds());
        assertEquals(2000L, panel.outputTokenCount());
        assertEquals(30.0, panel.requestCount());
        assertEquals(2.0, panel.gwpTotal().value(), 1e-4);
        assertEquals(LLMProvider.mistralai, panel.provider());
    }

    private record TestAsset(AssetIdentity identity, AssetType type, AssetProfile profile) implements Asset {}

    private static Footprint createFootprint(float gwp, float pe, float adpe)
    {
        Footprint.FootprintImpact gwpImpact = new Footprint.FootprintImpact("kgCO2eq", "GWP",
            new Footprint.FootprintImpact.FootprintImpactItem(0f, List.of()),
            new Footprint.FootprintImpact.FootprintImpactItem(gwp, List.of()));
        Footprint.FootprintImpact peImpact = new Footprint.FootprintImpact("MJ", "PE",
            new Footprint.FootprintImpact.FootprintImpactItem(0f, List.of()),
            new Footprint.FootprintImpact.FootprintImpactItem(pe, List.of()));
        Footprint.FootprintImpact adpeImpact = new Footprint.FootprintImpact("kgSbeq", "ADPe",
            new Footprint.FootprintImpact.FootprintImpactItem(0f, List.of()),
            new Footprint.FootprintImpact.FootprintImpactItem(adpe, List.of()));
        return new Footprint(gwpImpact, peImpact, adpeImpact);
    }

    private static EcologitsEstimationResponse createEstimationResponse(double gwp, double pe, double adpe, double energy)
    {
        EcologitsEstimationResponse.Metric gwpM = new EcologitsEstimationResponse.Metric("gwp", "GWP", EcologitsEstimationResponse.Metric.EcologitsRange.of(gwp), "kgCO2eq");
        EcologitsEstimationResponse.Metric peM = new EcologitsEstimationResponse.Metric("pe", "PE", EcologitsEstimationResponse.Metric.EcologitsRange.of(pe), "MJ");
        EcologitsEstimationResponse.Metric adpeM = new EcologitsEstimationResponse.Metric("adpe", "ADPe", EcologitsEstimationResponse.Metric.EcologitsRange.of(adpe), "kgSbeq");
        EcologitsEstimationResponse.Metric energyM = new EcologitsEstimationResponse.Metric("energy", "Energy", EcologitsEstimationResponse.Metric.EcologitsRange.of(energy), "kWh");
        EcologitsEstimationResponse.Impacts impacts = new EcologitsEstimationResponse.Impacts(
            energyM, gwpM, adpeM, peM, null, null, null
        );
        return new EcologitsEstimationResponse(impacts);
    }
}
