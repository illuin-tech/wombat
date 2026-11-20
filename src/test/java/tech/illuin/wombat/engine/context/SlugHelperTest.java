package tech.illuin.wombat.engine.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlugHelperTest
{
    @Test
    void slugify_makesAnyIdValid()
    {
        assertEquals("worker-1-chatbot", SlugHelper.slugify("worker-1.chatbot"));
        assertEquals("my_cluster-eu", SlugHelper.slugify("  My_Cluster..EU! "));
        assertEquals("test-llm", SlugHelper.slugify("test-llm"));
        assertEquals("asset", SlugHelper.slugify("..."));
        assertEquals(SlugHelper.MAX_LENGTH, SlugHelper.slugify("x".repeat(80)).length());
        assertTrue(SlugHelper.slugify("Ünïcode.Ассет").matches(SlugHelper.PATTERN));
    }
}
