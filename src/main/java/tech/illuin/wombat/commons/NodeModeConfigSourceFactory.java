package tech.illuin.wombat.commons;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.PropertiesConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.persistence.BackendMode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static tech.illuin.wombat.commons.ConfigSourceFactories.NODE_MODE;
import static tech.illuin.wombat.persistence.PersistenceBackendConfigSourceFactory.PERSISTENCE_BACKEND_MODE_KEY;

@StaticInitSafe
public class NodeModeConfigSourceFactory implements ConfigSourceFactory
{
    private static final Logger logger = LoggerFactory.getLogger(NodeModeConfigSourceFactory.class);

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context)
    {
        String value = context.getValue("wombat.mode").getValue();
        NodeMode mode = value == null ? NodeMode.standalone : NodeMode.parse(value);

        Map<String, String> properties = new HashMap<>();
        if (mode == NodeMode.worker)
        {
            logger.debug("Overriding persistence backend for worker mode: set to in-memory sqlite");
            properties.put(PERSISTENCE_BACKEND_MODE_KEY, BackendMode.sqlite.name());
            properties.put("persistence.backend.sqlite.jdbc-url", "jdbc:sqlite::memory:");
        }

        logger.debug("Node mode established: {}", mode);
        return List.of(new PropertiesConfigSource(
            properties,
            NodeModeConfigSourceFactory.class.getSimpleName(),
            NODE_MODE.priority()
        ));
    }

    @Override
    public OptionalInt getPriority()
    {
        return OptionalInt.of(NODE_MODE.priority());
    }
}
