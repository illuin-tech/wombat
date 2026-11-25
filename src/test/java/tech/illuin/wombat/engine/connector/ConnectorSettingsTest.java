package tech.illuin.wombat.engine.connector;

import io.quarkus.arc.All;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.module.ModuleSettings;
import tech.illuin.wombat.module.boavizta.BoaviztaSettings;
import tech.illuin.wombat.module.ecologits.EcologitsSettings;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The other tests run the Boavizta and Ecologits modules on mocked clients ({@link ConnectorClientMocks}): this one
 * checks that {@code wombat.connector.*} reaches their settings, without which they would quietly fall back to the
 * public APIs.
 */
@QuarkusTest
class ConnectorSettingsTest
{
    @Inject @All
    List<ModuleSettings> settings;

    @Test
    void connectorPropertiesBecomeTheSettingsOfTheirModules()
    {
        assertEquals(
            Set.of(
                new BoaviztaSettings("http://localhost:0/boavizta/v1/", "http://proxy.example.com:3128"),
                new EcologitsSettings("http://localhost:0/ecologits/", null)
            ),
            Set.copyOf(this.settings)
        );
    }
}
