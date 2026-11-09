package tech.illuin.wombat.engine.secret.audit;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.security.Caller;

@ApplicationScoped
public class LogAuditService implements SecretAuditService
{
    private static final Logger logger = LoggerFactory.getLogger(LogAuditService.class);

    @Override
    public void audit(SecurityIdentity identity, AuditableOperation operation, AuditableOutcome outcome)
    {
        String safeActor = Caller.subject(identity).replace('\n', '_').replace('\r', '_');
        logger.info("Secret management actor={} operation={} outcome={}", safeActor, operation, outcome);
    }
}
