package tech.illuin.wombat.engine.monitor;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.engine.module.runtime.CoreRuntime;
import tech.illuin.wombat.engine.module.runtime.RuntimeAssetMonitor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

@ApplicationScoped
public class MonitorConfig
{
    @Singleton
    public MonitoredEnvironments provideMonitoredEnvironments(WombatProperties wombat, YAMLMapper mapper, Validator validator)
    {
        String location = wombat.monitor().environmentsFile();
        try (InputStream in = open(location))
        {
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

    private static InputStream open(String location) throws IOException
    {
        Path path = Path.of(location);
        if (Files.isReadable(path))
            return Files.newInputStream(path);

        InputStream classpath = Thread.currentThread().getContextClassLoader().getResourceAsStream(location);
        if (classpath != null)
            return classpath;

        throw new IllegalStateException("Monitored resources file not found on filesystem or classpath: '" + location + "'");
    }
}
