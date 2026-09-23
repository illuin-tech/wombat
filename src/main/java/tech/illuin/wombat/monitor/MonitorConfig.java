package tech.illuin.wombat.monitor;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import tech.illuin.wombat.core.WombatCore;
import tech.illuin.wombat.core.source.AssetMonitor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class MonitorConfig
{
    @Singleton
    public MonitoredEnvironments provideMonitoredEnvironments(MonitorProperties properties, YAMLMapper mapper)
    {
        String location = properties.environmentsFile();
        try (InputStream in = open(location))
        {
            return mapper.readValue(in, MonitoredEnvironments.class);
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to load monitored environments from '" + location + "'", e);
        }
    }

    @Singleton
    public AssetMonitor provideMonitor(WombatCore core)
    {
        return core.createMonitor();
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
