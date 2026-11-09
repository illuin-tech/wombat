package tech.illuin.wombat.engine.secret.database.crypto;

import jakarta.enterprise.context.ApplicationScoped;
import tech.illuin.wombat.engine.secret.SecretProperties;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class LocalKeyEncryptionKeyProvider implements KeyEncryptionKeyProvider
{
    private final String activeKeyId;
    private final Map<String, byte[]> keys;

    public LocalKeyEncryptionKeyProvider(SecretProperties properties)
    {
        if (properties.writeMode() != SecretProperties.WriteMode.database)
        {
            this.activeKeyId = null;
            this.keys = Map.of();
            return;
        }

        Map<String, byte[]> decodedKeys = new HashMap<>();
        try {
            for (Map.Entry<String, String> entry : properties.database().kek().entrySet())
            {
                if (entry.getKey().isBlank())
                    throw new IllegalArgumentException("Database KEK identifiers must not be blank");

                byte[] key = decodeKey(entry.getValue());
                if (key.length != 32)
                {
                    Arrays.fill(key, (byte) 0);
                    throw new IllegalArgumentException("Database KEKs must be 256-bit AES keys");
                }
                decodedKeys.put(entry.getKey(), key);
            }

            this.activeKeyId = properties.database().activeKekId()
                .filter(id -> !id.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("An active database KEK identifier is required"));
            if (!decodedKeys.containsKey(this.activeKeyId))
                throw new IllegalArgumentException("The active database KEK identifier has no configured key");
            this.keys = Map.copyOf(decodedKeys);
        }
        catch (IllegalArgumentException e) {
            decodedKeys.values().forEach(key -> Arrays.fill(key, (byte) 0));
            throw e;
        }
    }

    private static byte[] decodeKey(String encoded)
    {
        try {
            return Base64.getDecoder().decode(encoded);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Database KEKs must be valid Base64-encoded AES-256 keys", e);
        }
    }

    @Override
    public String activeKeyId()
    {
        if (this.activeKeyId == null)
            throw new IllegalStateException("Database secret storage is not selected");
        return this.activeKeyId;
    }

    @Override
    public byte[] key(String id)
    {
        byte[] key = this.keys.get(id);
        if (key == null)
            throw new IllegalStateException("The requested database KEK is not configured");
        return key.clone();
    }
}
