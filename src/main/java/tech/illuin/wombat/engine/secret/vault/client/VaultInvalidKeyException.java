package tech.illuin.wombat.engine.secret.vault.client;

import jakarta.ws.rs.BadRequestException;

public class VaultInvalidKeyException extends BadRequestException
{
    public VaultInvalidKeyException()
    {
        super("Secret key is not a valid relative Vault path");
    }
}
