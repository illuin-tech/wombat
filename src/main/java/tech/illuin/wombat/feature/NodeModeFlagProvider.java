package tech.illuin.wombat.feature;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.spi.ComponentOrder;
import io.quarkiverse.flags.spi.FlagProvider;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Singleton;
import tech.illuin.wombat.commons.WombatProperties;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Singleton
@Identifier(NodeModeFlagProvider.ID)
@ComponentOrder(before = NodeModeFlagProvider.QUARKUS_CONFIG_ID)
public class NodeModeFlagProvider implements FlagProvider
{
    static final String ID = "wombat.mode";
    static final String QUARKUS_CONFIG_ID = "quarkus.config";

    private final List<Flag> flags;

    public NodeModeFlagProvider(WombatProperties wombat)
    {
        this.flags = Arrays.stream(Feature.values())
            .map(feature -> Flag.builder(feature.flag())
                .setEnabled(feature.enabledIn(wombat.mode()))
                .setOrigin(ID)
                .build()
            )
            .toList();
    }

    @Override
    public Uni<Collection<Flag>> getFlags()
    {
        return Uni.createFrom().item(this.flags);
    }
}
