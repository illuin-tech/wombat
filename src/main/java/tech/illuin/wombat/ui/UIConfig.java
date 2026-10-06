package tech.illuin.wombat.ui;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;

@ApplicationScoped
public class UIConfig
{
    @Named("currentUser")
    @Dependent
    public CurrentUser provideCurrentUser(SecurityIdentity identity)
    {
        return CurrentUser.from(identity);
    }
}
