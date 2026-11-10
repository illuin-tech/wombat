package tech.illuin.wombat.engine.monitor;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.secret.SecretAware;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.engine.secret.SecretProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Refuses to start when a configured asset needs a credential that no configured secret source provides.
 * <p>
 * Without this the failure would surface on the first scrape — a warning buried in a heartbeat log, hours after a
 * deployment that looked healthy. The check runs before reconciliation so invalid configuration is not persisted.
 */
@ApplicationScoped
public class RequiredSecretsCheck
{
    private final MonitoredEnvironments monitoredEnvironments;
    private final SecretResolver secrets;
    private final SecretProperties properties;

    private static final Logger logger = LoggerFactory.getLogger(RequiredSecretsCheck.class);
    /**
     * The early check preserves the existing disabled-store behavior; enabled writable stores are checked after
     * persistence setup and before the reconciler.
     */
    static final int STARTUP_PRIORITY_SECRETS_EARLY = 1;
    static final int STARTUP_PRIORITY_SECRETS_WRITABLE_BACKEND = 1500;

    public RequiredSecretsCheck(MonitoredEnvironments monitoredEnvironments, SecretResolver secrets, SecretProperties properties)
    {
        this.monitoredEnvironments = monitoredEnvironments;
        this.secrets = secrets;
        this.properties = properties;
    }

    void onStartEarly(@Observes @Priority(STARTUP_PRIORITY_SECRETS_EARLY) StartupEvent event)
    {
        if (this.properties.writeMode() == SecretProperties.WriteMode.disabled)
            validateRequiredSecrets();
    }

    void onStartAfterPersistenceSetup(@Observes @Priority(STARTUP_PRIORITY_SECRETS_WRITABLE_BACKEND) StartupEvent event)
    {
        if (this.properties.writeMode() == SecretProperties.WriteMode.database
            || this.properties.writeMode() == SecretProperties.WriteMode.vault)
            validateRequiredSecrets();
    }

    private void validateRequiredSecrets()
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
                + ". Configure each missing key in an available secret source before starting the application.");
        }

        logger.info("Resolved {} required secret(s) from configured secret sources", required.size());
    }
}
