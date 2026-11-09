package tech.illuin.wombat.engine.secret;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.secret.SecretMetadata;
import tech.illuin.wombat.core.secret.SecretWriter;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretControllerWriterSelectionTest
{
    @Test
    void managesSecretsThroughABackendNeutralWriter()
    {
        InMemorySecretWriter writer = new InMemorySecretWriter();
        SecretController controller = new SecretController(writer);

        SecretWriteRequest request = new SecretWriteRequest("token", "private-value");
        assertFalse(request.toString().contains("private-value"));
        controller.put(request);

        assertEquals("private-value", writer.values.get("token"));
        assertEquals("token", controller.list().payload().getFirst().key());
        controller.delete("token");
        assertTrue(writer.values.isEmpty());
    }

    private static class InMemorySecretWriter implements SecretWriter
    {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public List<SecretMetadata> listSecrets()
        {
            Instant now = Instant.now();
            return this.values.keySet().stream().map(key -> new SecretMetadata(key, now, now)).toList();
        }

        @Override
        public void putSecret(String key, String value)
        {
            this.values.put(key, value);
        }

        @Override
        public boolean deleteSecret(String key)
        {
            return this.values.remove(key) != null;
        }
    }
}