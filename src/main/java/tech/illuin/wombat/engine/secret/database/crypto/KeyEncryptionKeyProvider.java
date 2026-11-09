package tech.illuin.wombat.engine.secret.database.crypto;

public interface KeyEncryptionKeyProvider
{
    String activeKeyId();

    byte[] key(String id);
}
