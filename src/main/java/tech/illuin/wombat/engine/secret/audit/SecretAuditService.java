package tech.illuin.wombat.engine.secret.audit;

import io.quarkus.security.identity.SecurityIdentity;

public interface SecretAuditService
{
    void audit(SecurityIdentity identity, AuditableOperation operation, AuditableOutcome outcome);
}
