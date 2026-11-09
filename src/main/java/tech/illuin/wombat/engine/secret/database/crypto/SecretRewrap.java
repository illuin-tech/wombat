package tech.illuin.wombat.engine.secret.database.crypto;

public record SecretRewrap(
    int scanned,
    int rewrapped,
    int failures,
    int conflicts,
    long remaining
) {
    public boolean complete()
    {
        return this.remaining == 0;
    }
}
