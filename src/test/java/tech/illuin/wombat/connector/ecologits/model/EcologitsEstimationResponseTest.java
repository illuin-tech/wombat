package tech.illuin.wombat.connector.ecologits.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import tech.illuin.wombat.core.connector.ecologits.connector.model.EcologitsEstimationResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EcologitsEstimationResponseTest
{

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserialize_scalarValue_mapsToDegenerateRange() throws Exception
    {
        String json = """
            {"impacts": {"energy": {"type": "energy", "name": "Energy", "value": 3.005745642764265E-4, "unit": "kWh"}}}
            """;

        EcologitsEstimationResponse response = mapper.readValue(json, EcologitsEstimationResponse.class);

        assertEquals(3.005745642764265E-4, response.impacts().energy().value().min());
        assertEquals(3.005745642764265E-4, response.impacts().energy().value().max());
    }

    @Test
    void deserialize_rangeValue_mapsMinAndMax() throws Exception
    {
        String json = """
            {"impacts": {"gwp": {"type": "GWP", "name": "Global Warming Potential", "value": {"min": 0.001, "max": 0.003}, "unit": "kgCO2eq"}}}
            """;

        EcologitsEstimationResponse response = mapper.readValue(json, EcologitsEstimationResponse.class);

        assertEquals(0.001, response.impacts().gwp().value().min());
        assertEquals(0.003, response.impacts().gwp().value().max());
    }
}
