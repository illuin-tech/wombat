package tech.illuin.wombat.connector.ecologits;

import tech.illuin.wombat.core.connector.ecologits.connector.model.EcologitsEstimationResponse;

public final class EcologitsTestData
{

    private EcologitsTestData() {}

    public static EcologitsEstimationResponse fakeEstimation()
    {
        return new EcologitsEstimationResponse(new EcologitsEstimationResponse.Impacts(
            metric("energy", "kWh", 0.1, 0.2),
            metric("GWP", "kgCO2eq", 0.001, 0.003),
            metric("ADPe", "kgSbeq", 0.0000001, 0.0000002),
            metric("PE", "MJ", 0.01, 0.02),
            metric("WCF", "L", 0.001, 0.002),
            new EcologitsEstimationResponse.Usage(
                "usage", "Usage",
                metric("energy", "kWh", 0.08, 0.16),
                metric("GWP", "kgCO2eq", 0.0008, 0.0024),
                metric("ADPe", "kgSbeq", 0.00000008, 0.00000016),
                metric("PE", "MJ", 0.008, 0.016),
                metric("WCF", "L", 0.0008, 0.0016)
            ),
            new EcologitsEstimationResponse.Embodied(
                "embodied", "Embodied",
                metric("GWP", "kgCO2eq", 0.0002, 0.0006),
                metric("ADPe", "kgSbeq", 0.00000002, 0.00000004),
                metric("PE", "MJ", 0.002, 0.004)
            )
        ));
    }

    private static EcologitsEstimationResponse.Metric metric(String name, String unit, double min, double max)
    {
        return new EcologitsEstimationResponse.Metric("impact", name, new EcologitsEstimationResponse.Metric.EcologitsRange(min, max), unit);
    }
}
