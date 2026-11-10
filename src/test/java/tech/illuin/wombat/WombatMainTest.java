package tech.illuin.wombat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WombatMainTest
{
    @AfterEach
    void clearMode()
    {
        System.clearProperty(WombatMain.MODE_PROPERTY);
    }

    @Test
    void modeOptionSetsTheModeProperty()
    {
        String[] remaining = WombatMain.applyOptions("--mode", "worker", "other");

        assertEquals("worker", System.getProperty(WombatMain.MODE_PROPERTY));
        assertArrayEquals(new String[] { "other" }, remaining);
    }

    @Test
    void modeOptionAcceptsAnInlineValue()
    {
        String[] remaining = WombatMain.applyOptions("--mode=aggregator");

        assertEquals("aggregator", System.getProperty(WombatMain.MODE_PROPERTY));
        assertArrayEquals(new String[0], remaining);
    }

    @Test
    void withoutModeOptionThePropertyIsLeftUnset()
    {
        WombatMain.applyOptions("other");

        assertNull(System.getProperty(WombatMain.MODE_PROPERTY));
    }

    @Test
    void modeOptionWithoutValueIsRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> WombatMain.applyOptions("--mode"));
    }
}
