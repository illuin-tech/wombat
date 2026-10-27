package tech.illuin.wombat.ui;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;
import tech.illuin.wombat.security.AuthProperties;

@ApplicationScoped
public class UIConfig
{
    @Named("currentUser")
    @Dependent
    public CurrentUser provideCurrentUser(SecurityIdentity identity, AuthProperties authProperties)
    {
        return CurrentUser.from(identity, authProperties.displayNameClaims());
    }
}
