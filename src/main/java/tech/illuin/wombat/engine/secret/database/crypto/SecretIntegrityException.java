package tech.illuin.wombat.engine.secret.database.crypto;

public class SecretIntegrityException extends RuntimeException
{
    public SecretIntegrityException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
