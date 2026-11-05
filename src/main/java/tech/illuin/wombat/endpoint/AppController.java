package tech.illuin.wombat.endpoint;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.security.AuthProperties;

import java.util.Locale;

@Path("api/app")
public class AppController
{
    private final SecurityIdentity identity;
    private final AuthProperties authProperties;
    private final UIProperties uiProperties;

    public AppController(SecurityIdentity identity, AuthProperties authProperties, UIProperties uiProperties)
    {
        this.identity = identity;
        this.authProperties = authProperties;
        this.uiProperties = uiProperties;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(
        responseCode = "200",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                properties = {
                    @SchemaProperty(name = "status", implementation = Status.class),
                    @SchemaProperty(name = "payload", implementation = AppInfo.class)
                }
            )
        )
    )
    public Response<AppInfo> get()
    {
        UIProperties.MaxDateRange maxDateRange = this.uiProperties.maxDateRange();
        AppInfo info = new AppInfo(
            new AppInfo.Auth(this.authProperties.enabled()),
            CurrentUser.from(this.identity, this.authProperties),
            new AppInfo.Ui(new AppInfo.MaxDateRange(
                maxDateRange.asDuration().toMillis(),
                maxDateRange.duration() + " " + maxDateRange.unit().name().toLowerCase(Locale.ROOT)
            ))
        );
        return Response.success(info);
    }
}
