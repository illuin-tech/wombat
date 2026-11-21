package tech.illuin.wombat.engine.monitor;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.engine.module.runtime.CoreRuntime;
import tech.illuin.wombat.engine.module.runtime.RuntimeAssetMonitor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class MonitorConfig
{
    private static final Logger logger = LoggerFactory.getLogger(MonitorConfig.class);

    @Singleton
    public MonitoredEnvironments provideMonitoredEnvironments(WombatProperties wombat, YAMLMapper mapper, Validator validator)
    {
        return load(wombat.monitor().environmentsFile(), mapper, validator);
    }

    /**
     * The file is optional: without it nothing is provisioned, and environments are managed through the API.
     */
    static MonitoredEnvironments load(String location, YAMLMapper mapper, Validator validator)
    {
        try (InputStream in = open(location))
        {
            if (in == null)
            {
                logger.info("No monitored environments file at '{}', no environment is provisioned", location);
                return new MonitoredEnvironments(Map.of());
            }
            MonitoredEnvironments monitoredEnvironments = mapper.readValue(in, MonitoredEnvironments.class);
            Set<ConstraintViolation<MonitoredEnvironments>> violations = validator.validate(monitoredEnvironments);
            if (!violations.isEmpty())
                throw new ConstraintViolationException(violations);
            return monitoredEnvironments;
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to load monitored environments from '" + location + "'", e);
        }
    }

    /**
     * Delegates to the current deployment on every call, so that its holders follow redeploys.
     */
    @Singleton
    public AssetMonitor provideMonitor(CoreRuntime runtime)
    {
        return new RuntimeAssetMonitor(runtime);
    }

    @Singleton
    public MonitorService provideMonitorService(AssetMonitor assetMonitor)
    {
        return new MonitorService(assetMonitor);
    }

    /**
     * The file at the location, else the classpath resource, else {@code null}. A directory counts as no file: Docker
     * mounts one when the host file of a bind mount is missing. A file that can't be read fails.
     */
    private static InputStream open(String location) throws IOException
    {
        Path path = Path.of(location);
        if (Files.isRegularFile(path))
            return Files.newInputStream(path);

        return Thread.currentThread().getContextClassLoader().getResourceAsStream(location);
    }
}
