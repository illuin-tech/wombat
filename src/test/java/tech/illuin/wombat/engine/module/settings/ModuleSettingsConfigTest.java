package tech.illuin.wombat.engine.module.settings;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.connector.ConnectorClientMocks;
import tech.illuin.wombat.module.boavizta.BoaviztaSettings;
import tech.illuin.wombat.module.ecologits.EcologitsSettings;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The other tests run the Boavizta and Ecologits modules on mocked clients ({@link ConnectorClientMocks}): this one
 * checks that {@code wombat.module.settings} reaches their settings, without which they would quietly fall back to the
 * public APIs.
 */
@QuarkusTest
class ModuleSettingsConfigTest
{
    @Inject
    ModuleSettingsReader reader;

    @Test
    void moduleSettingsReachTheirModules()
    {
        assertEquals(
            Set.of(
                new BoaviztaSettings("http://localhost:0/boavizta/v1/", "http://proxy.example.com:3128"),
                new EcologitsSettings("http://localhost:0/ecologits/", null)
            ),
            Set.copyOf(this.reader.read())
        );
    }
}
