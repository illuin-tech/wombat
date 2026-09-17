package tech.illuin.wombat.monitor;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.secret.SecretAware;
import tech.illuin.wombat.core.secret.SecretResolver;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Refuses to start when a configured asset needs a credential the environment does not provide.
 * <p>
 * Without this the failure would surface on the first scrape — a warning buried in a heartbeat log, hours after a
 * deployment that looked healthy. Running before the reconciler also means a misconfigured environment never reaches
 * the database.
 */
@ApplicationScoped
public class RequiredSecretsCheck
{
    private final MonitoredEnvironments monitoredEnvironments;
    private final SecretResolver secrets;

    private static final Logger logger = LoggerFactory.getLogger(RequiredSecretsCheck.class);
    /**
     * Ahead of every other startup observer — the persistence engine (900) and the reconciler (2000) — so a
     * deployment missing a credential stops before it migrates, restores a backup or writes an asset.
     */
    static final int STARTUP_PRIORITY_SECRETS = 1;

    public RequiredSecretsCheck(MonitoredEnvironments monitoredEnvironments, SecretResolver secrets)
    {
        this.monitoredEnvironments = monitoredEnvironments;
        this.secrets = secrets;
    }

    void onStart(@Observes @Priority(STARTUP_PRIORITY_SECRETS) StartupEvent event)
    {
        Set<String> required = new LinkedHashSet<>();
        for (Asset asset : this.monitoredEnvironments.allAssets())
        {
            if (asset instanceof SecretAware secretAware)
                required.addAll(secretAware.requiredSecretKeys());
        }

        Set<String> missing = new LinkedHashSet<>();
        for (String key : required)
        {
            if (this.secrets.find(key).isEmpty())
                missing.add(key);
        }

        if (!missing.isEmpty())
        {
            throw new IllegalStateException(
                "Missing required secret(s): " + String.join(", ", missing)
                + ". Set the corresponding environment variable(s) before starting the application.");
        }

        logger.info("Resolved {} required secret(s) from the environment", required.size());
    }
}
