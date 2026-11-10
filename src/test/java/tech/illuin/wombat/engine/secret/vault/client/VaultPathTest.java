package tech.illuin.wombat.engine.secret.vault.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VaultPathTest
{
    @Test
    void mapsNestedKeysBelowPrefix()
    {
        assertEquals("wombat/team/api-key", VaultPath.keyPath("wombat", "team/api-key"));
    }

    @Test
    void acceptsOnlyRelativeUnreservedSegments()
    {
        for (String invalid : new String[]{null, "", " ", "/key", "key/", "a//b", ".", "..", "a/../b", "a\\b", "a?b", "a#b", "a%b", "a:b", "a\nb"})
        {
            VaultInvalidKeyException exception = assertThrows(
                VaultInvalidKeyException.class,
                () -> VaultPath.keyPath("wombat", invalid),
                invalid
            );
            assertEquals(400, exception.getResponse().getStatus());
        }
    }

    @Test
    void validatesTheConfiguredPrefix()
    {
        for (String invalid : new String[]{null, "", "/wombat", "wombat/", "wombat//secrets", "../wombat", "wombat?secret"})
            assertThrows(IllegalStateException.class, () -> VaultPath.validatePrefix(invalid), invalid);
    }
}
