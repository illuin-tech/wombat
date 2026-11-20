package tech.illuin.wombat.engine.simulation;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.engine.context.SlugHelper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulationAssetServiceTest
{
    @Test
    void uniqueId_suffixesATakenId()
    {
        assertEquals("chatbot", SimulationAssetService.uniqueId("chatbot", Set.of()));
        assertEquals("chatbot-2", SimulationAssetService.uniqueId("chatbot", Set.of("chatbot")));
        assertEquals("chatbot-3", SimulationAssetService.uniqueId("chatbot", Set.of("chatbot", "chatbot-2")));
    }

    @Test
    void uniqueId_staysWithinTheMaximumLength()
    {
        String longest = "x".repeat(SlugHelper.MAX_LENGTH);

        assertEquals("x".repeat(SlugHelper.MAX_LENGTH - 2) + "-2", SimulationAssetService.uniqueId(longest, Set.of(longest)));
    }

    @Test
    void identity_isApartFromEveryEnvironment()
    {
        assertEquals(AssetIdentity.of("chatbot", "simulation:what-if", "acme.llm"), SimulationAssetService.identity("what-if", "chatbot", "acme.llm"));
    }
}
