package tech.illuin.wombat.engine.monitor;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.illuin.wombat.engine.module.WombatModuleConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The environments file is an optional way to provision environments: the app must start without it.
 */
class MonitorConfigTest
{
    @TempDir
    Path directory;

    @Test
    void aMissingFileProvisionsNothing()
    {
        MonitoredEnvironments environments = load(this.directory.resolve("monitored-environments.yaml").toString());

        assertTrue(environments.environments().isEmpty());
    }

    @Test
    void aDirectoryProvisionsNothing() throws IOException
    {
        /* What Docker bind-mounts when the host file is missing */
        Path mounted = Files.createDirectory(this.directory.resolve("monitored-environments.yaml"));

        MonitoredEnvironments environments = load(mounted.toString());

        assertTrue(environments.environments().isEmpty());
    }

    @Test
    void anEmptyEnvironmentsKeyProvisionsNothing() throws IOException
    {
        Path file = Files.writeString(this.directory.resolve("monitored-environments.yaml"), "environments:\n");

        MonitoredEnvironments environments = load(file.toString());

        assertEquals(List.of(), environments.allAssets());
    }

    private static MonitoredEnvironments load(String location)
    {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        YAMLMapper mapper = new WombatModuleConfig().provideYAMLMapper(List.of());
        return MonitorConfig.load(location, mapper, validator);
    }
}
