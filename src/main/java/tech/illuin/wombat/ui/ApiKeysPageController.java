package tech.illuin.wombat.ui;

import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import tech.illuin.wombat.security.AuthProperties;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;

import java.util.List;

/** Admin page listing, creating and revoking API keys, through the {@code /api/keys} endpoints. */
@Path("api-keys")
@PermissionsAllowed(Permission.Names.UI_APIKEYS_MANAGE)
public class ApiKeysPageController
{
    private final SecurityIdentity identity;
    private final AuthProperties properties;

    public ApiKeysPageController(SecurityIdentity identity, AuthProperties properties)
    {
        this.identity = identity;
        this.properties = properties;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get()
    {
        List<String> grantable = Caller.permissions(this.identity).stream()
            .filter(Permission::grantableToApiKeys)
            .map(Permission::value)
            .toList();

        return Templates.apiKeys(new Templates.ApiKeysPage(
            this.properties.enabled(),
            grantable,
            this.properties.apiKeys().maxLifetime().toDays(),
            this.properties.apiKeys().allowNeverExpiring()
        ));
    }
}
