package tech.illuin.wombat.connector.boavizta;

import tech.illuin.wombat.core.connector.boavizta.connector.model.BoaviztaInstanceConfigResponse;
import tech.illuin.wombat.core.connector.boavizta.connector.model.BoaviztaImpactResponse;

import java.util.List;
import java.util.Map;

public final class BoaviztaTestData
{

    private BoaviztaTestData() {}

    public static BoaviztaInstanceConfigResponse fakeInstanceConfig(int vcpu)
    {
        return new BoaviztaInstanceConfigResponse(
            new BoaviztaInstanceConfigResponse.IntConfigItem(vcpu),
            new BoaviztaInstanceConfigResponse.IntConfigItem(16),
            new BoaviztaInstanceConfigResponse.IntConfigItem(0),
            new BoaviztaInstanceConfigResponse.IntConfigItem(0),
            new BoaviztaInstanceConfigResponse.IntConfigItem(0),
            new BoaviztaInstanceConfigResponse.StringConfigItem("linux")
        );
    }

    public static BoaviztaImpactResponse fakeImpactResponse()
    {
        BoaviztaImpactResponse.Impact gwp = impact("kgCO2eq", "Global warming potential", 100f, 200f);
        BoaviztaImpactResponse.Impact pe = impact("MJ", "Primary energy", 300f, 400f);
        BoaviztaImpactResponse.Impact adp = impact("kgSbeq", "Abiotic resource depletion", 0.001f, 0.002f);
        return new BoaviztaImpactResponse(Map.of("gwp", gwp, "pe", pe, "adp", adp), 1);
    }

    private static BoaviztaImpactResponse.Impact impact(String unit, String description, float embedded, float use)
    {
        return new BoaviztaImpactResponse.Impact(
            unit, description,
            new BoaviztaImpactResponse.Impact.ImpactItem(embedded, embedded, embedded, List.of()),
            new BoaviztaImpactResponse.Impact.ImpactItem(use, use, use, List.of())
        );
    }
}
