package tech.illuin.wombat.endpoint.secret;

import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.secret.SecretMetadata;
import tech.illuin.wombat.core.secret.SecretWriter;
import tech.illuin.wombat.engine.secret.SecretController;
import tech.illuin.wombat.engine.secret.SecretWriteRequest;
import tech.illuin.wombat.engine.secret.audit.LogAuditService;

import java.security.Principal;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecretControllerWriterSelectionTest
{
    @Test
    void managesSecretsThroughABackendNeutralWriter()
    {
        InMemorySecretWriter writer = new InMemorySecretWriter();
        SecurityIdentity identity = mock(SecurityIdentity.class);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("test-admin");
        when(identity.getPrincipal()).thenReturn(principal);
        SecretController controller = new SecretController(writer, identity, new LogAuditService());

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