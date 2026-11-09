package tech.illuin.wombat.engine.secret.database.crypto;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.engine.secret.database.persistence.SecretEntity;
import tech.illuin.wombat.engine.secret.database.persistence.SecretRepository;

import java.util.List;

@ApplicationScoped
public class SecretRewrapService
{

    private final SecretRepository secrets;
    private final SecretCryptography cryptography;
    private final KeyEncryptionKeyProvider keyProvider;

    static final int BATCH_SIZE = 100;
    private static final int MAX_BATCHES_PER_REQUEST = 10;
    private static final Logger logger = LoggerFactory.getLogger(SecretRewrapService.class);

    @Inject
    public SecretRewrapService(SecretRepository secrets, SecretCryptography cryptography, KeyEncryptionKeyProvider keyProvider)
    {
        this.secrets = secrets;
        this.cryptography = cryptography;
        this.keyProvider = keyProvider;
    }

    public SecretRewrap rewrapBatch()
    {
        return rewrapBatch(MAX_BATCHES_PER_REQUEST);
    }

    SecretRewrap rewrapBatch(int maxBatches)
    {
        if (maxBatches < 1 || maxBatches > MAX_BATCHES_PER_REQUEST)
            throw new IllegalArgumentException("Rewrap batch count is outside the allowed range");

        String activeKekId = this.keyProvider.activeKeyId();
        String afterKey = null;
        int scanned = 0;
        int rewrapped = 0;
        int failures = 0;
        int conflicts = 0;
        int batches = 0;

        for (int i = 0 ; i < maxBatches ; ++i)
        {
            checkInterrupted();
            String cursor = afterKey;
            BatchResult batch = QuarkusTransaction.requiringNew().call(() -> processBatch(activeKekId, cursor));
            if (batch.scanned() == 0)
                break;

            batches++;
            scanned += batch.scanned();
            rewrapped += batch.rewrapped();
            failures += batch.failures();
            conflicts += batch.conflicts();
            afterKey = batch.lastKey();
            if (batch.scanned() < BATCH_SIZE)
                break;
        }

        checkInterrupted();
        long remaining = QuarkusTransaction.requiringNew().call(() -> this.secrets.countNeedingRewrap(activeKekId));
        logger.info(
            "Secret KEK rewrap completed: {} scanned, {} rewrapped, {} failures, {} conflicts, {} remaining across {} batches",
            scanned,
            rewrapped,
            failures,
            conflicts,
            remaining,
            batches
        );
        return new SecretRewrap(scanned, rewrapped, failures, conflicts, remaining);
    }

    private BatchResult processBatch(String activeKekId, String afterKey)
    {
        List<SecretEntity> rows = this.secrets.findNeedingRewrap(activeKekId, afterKey, BATCH_SIZE);
        int rewrapped = 0;
        int failures = 0;
        int conflicts = 0;
        for (SecretEntity row : rows)
        {
            try {
                SecretCryptography.RewrappedDek wrapped = this.cryptography.rewrap(encrypted(row));
                if (this.secrets.updateWrappedDekIfRevisionMatches(
                    row.key,
                    row.revision,
                    wrapped.kekId(),
                    wrapped.wrappedDek(),
                    wrapped.wrappingNonce()
                ) == 1)
                    rewrapped++;
                else
                    conflicts++;
            }
            catch (IllegalStateException | SecretIntegrityException e) {
                failures++;
            }
        }
        return new BatchResult(rows.size(), rewrapped, failures, conflicts, rows.isEmpty() ? afterKey : rows.getLast().key);
    }

    private static SecretCryptography.EncryptedSecret encrypted(SecretEntity secret)
    {
        return new SecretCryptography.EncryptedSecret(
            secret.key,
            secret.kekId,
            secret.ciphertext,
            secret.payloadNonce,
            secret.wrappedDek,
            secret.wrappingNonce
        );
    }

    private static void checkInterrupted()
    {
        if (Thread.currentThread().isInterrupted())
            throw new IllegalStateException("Secret KEK rewrap was interrupted");
    }

    private record BatchResult(
        int scanned,
        int rewrapped,
        int failures,
        int conflicts,
        String lastKey
    ) {}
}
