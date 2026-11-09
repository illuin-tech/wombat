package tech.illuin.wombat.engine.secret.database.crypto;

import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

@ApplicationScoped
public class SecretCryptography
{
    private final KeyEncryptionKeyProvider keyProvider;
    private final SecureRandom random;

    private static final int DEK_SIZE = 32;
    private static final int NONCE_SIZE = 12;
    private static final int TAG_SIZE_BITS = 128;
    private static final String PAYLOAD_DOMAIN = "wombat-secret-payload-v1";
    private static final String WRAPPED_DEK_DOMAIN = "wombat-secret-dek-v1";

    public SecretCryptography(KeyEncryptionKeyProvider keyProvider)
    {
        this.keyProvider = keyProvider;
        this.random = new SecureRandom();
    }

    public EncryptedSecret encrypt(String secretKey, String value)
    {
        byte[] dataKey = null;
        byte[] kek = null;
        byte[] plaintext = null;
        try {
            String kekId = this.keyProvider.activeKeyId();
            kek = this.keyProvider.key(kekId);
            dataKey = new byte[DEK_SIZE];
            this.random.nextBytes(dataKey);
            byte[] payloadNonce = nonce();
            byte[] wrappingNonce = nonce();
            plaintext = value.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = crypt(Cipher.ENCRYPT_MODE, dataKey, payloadNonce, plaintext, payloadAad(secretKey));
            byte[] wrappedDek = crypt(Cipher.ENCRYPT_MODE, kek, wrappingNonce, dataKey, wrappedDekAad(secretKey, kekId));
            return new EncryptedSecret(secretKey, kekId, ciphertext, payloadNonce, wrappedDek, wrappingNonce);
        }
        catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt database secret", e);
        }
        finally {
            if (dataKey != null)
                Arrays.fill(dataKey, (byte) 0);
            if (kek != null)
                Arrays.fill(kek, (byte) 0);
            if (plaintext != null)
                Arrays.fill(plaintext, (byte) 0);
        }
    }

    public String decrypt(EncryptedSecret secret)
    {
        byte[] kek = this.keyProvider.key(secret.kekId());
        byte[] dataKey = null;
        byte[] plaintext = null;
        try {
            dataKey = crypt(
                Cipher.DECRYPT_MODE,
                kek,
                secret.wrappingNonce(),
                secret.wrappedDek(),
                wrappedDekAad(secret.secretKey(), secret.kekId())
            );
            plaintext = crypt(
                Cipher.DECRYPT_MODE,
                dataKey,
                secret.payloadNonce(),
                secret.ciphertext(),
                payloadAad(secret.secretKey())
            );
            return new String(plaintext, StandardCharsets.UTF_8);
        }
        catch (GeneralSecurityException e) {
            throw new SecretIntegrityException("Stored database secret failed integrity verification", e);
        }
        finally {
            Arrays.fill(kek, (byte) 0);
            if (dataKey != null)
                Arrays.fill(dataKey, (byte) 0);
            if (plaintext != null)
                Arrays.fill(plaintext, (byte) 0);
        }
    }

    public RewrappedDek rewrap(EncryptedSecret secret)
    {
        String activeKeyId = this.keyProvider.activeKeyId();
        if (secret.kekId().equals(activeKeyId))
            return new RewrappedDek(activeKeyId, secret.wrappedDek(), secret.wrappingNonce());

        byte[] oldKek = null;
        byte[] newKek = null;
        byte[] dataKey = null;
        byte[] newNonce = nonce();
        try {
            oldKek = this.keyProvider.key(secret.kekId());
            newKek = this.keyProvider.key(activeKeyId);
            dataKey = crypt(
                Cipher.DECRYPT_MODE,
                oldKek,
                secret.wrappingNonce(),
                secret.wrappedDek(),
                wrappedDekAad(secret.secretKey(), secret.kekId())
            );
            byte[] newWrappedDek = crypt(
                Cipher.ENCRYPT_MODE,
                newKek,
                newNonce,
                dataKey,
                wrappedDekAad(secret.secretKey(), activeKeyId)
            );
            return new RewrappedDek(activeKeyId, newWrappedDek, newNonce);
        }
        catch (GeneralSecurityException e) {
            throw new SecretIntegrityException("Stored database secret key failed integrity verification", e);
        }
        finally {
            if (oldKek != null)
                Arrays.fill(oldKek, (byte) 0);
            if (newKek != null)
                Arrays.fill(newKek, (byte) 0);
            if (dataKey != null)
                Arrays.fill(dataKey, (byte) 0);
        }
    }

    private static byte[] crypt(
        int mode,
        byte[] key,
        byte[] nonce,
        byte[] input,
        byte[] associatedData
    ) throws GeneralSecurityException
    {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_SIZE_BITS, nonce));
        cipher.updateAAD(associatedData);
        return cipher.doFinal(input);
    }

    private byte[] nonce()
    {
        byte[] nonce = new byte[NONCE_SIZE];
        this.random.nextBytes(nonce);
        return nonce;
    }

    private static byte[] payloadAad(String secretKey)
    {
        return encode(PAYLOAD_DOMAIN, secretKey);
    }

    private static byte[] wrappedDekAad(String secretKey, String kekId)
    {
        return encode(WRAPPED_DEK_DOMAIN, secretKey, kekId);
    }

    private static byte[] encode(String... values)
    {
        byte[][] encodedValues = new byte[values.length][];
        int size = 0;
        for (int i = 0; i < values.length; i++)
        {
            encodedValues[i] = values[i].getBytes(StandardCharsets.UTF_8);
            size = Math.addExact(Math.addExact(size, Integer.BYTES), encodedValues[i].length);
        }
        ByteBuffer buffer = ByteBuffer.allocate(size);
        for (byte[] encoded : encodedValues)
            buffer.putInt(encoded.length).put(encoded);
        return buffer.array();
    }

    public record EncryptedSecret(String secretKey, String kekId, byte[] ciphertext, byte[] payloadNonce, byte[] wrappedDek, byte[] wrappingNonce)
    {
        public EncryptedSecret
        {
            ciphertext = ciphertext.clone();
            payloadNonce = payloadNonce.clone();
            wrappedDek = wrappedDek.clone();
            wrappingNonce = wrappingNonce.clone();
        }

        @Override
        public byte[] ciphertext()
        {
            return this.ciphertext.clone();
        }

        @Override
        public byte[] payloadNonce()
        {
            return this.payloadNonce.clone();
        }

        @Override
        public byte[] wrappedDek()
        {
            return this.wrappedDek.clone();
        }

        @Override
        public byte[] wrappingNonce()
        {
            return this.wrappingNonce.clone();
        }
    }

    public record RewrappedDek(String kekId, byte[] wrappedDek, byte[] wrappingNonce)
    {
        public RewrappedDek
        {
            wrappedDek = wrappedDek.clone();
            wrappingNonce = wrappingNonce.clone();
        }

        @Override
        public byte[] wrappedDek()
        {
            return this.wrappedDek.clone();
        }

        @Override
        public byte[] wrappingNonce()
        {
            return this.wrappingNonce.clone();
        }
    }
}
