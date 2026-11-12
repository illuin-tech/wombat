package tech.illuin.wombat.endpoint;

import io.quarkus.resteasy.reactive.server.EndpointDisabled;
import io.quarkus.security.PermissionsAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import tech.illuin.wombat.commons.NodeMode;
import tech.illuin.wombat.commons.NodeModeConfigSourceFactory;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.source.persistence.WombatMetricPersister;
import tech.illuin.wombat.core.source.persistence.WombatPersistenceException;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.persistence.push.MetricPush;
import tech.illuin.wombat.security.Permission;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Receives metrics sampled by workers, see {@link tech.illuin.wombat.persistence.backend.push.PushBackendConfig}. Only
 * an {@link NodeMode#AGGREGATOR} serves it. Metrics are staged exactly like those of local sources, and land in the
 * database with the next sampling window.
 */
@Path("api/metrics")
@EndpointDisabled(name = NodeModeConfigSourceFactory.AGGREGATOR, stringValue = "false")
@PermissionsAllowed(Permission.Names.API_METRIC_WRITE)
public class MetricController
{
    private final WombatMetricPersister persister;
    private final Map<String, AssetType> types;

    public MetricController(
        WombatMetricPersister persister,
        List<WombatModule> modules
    ) {
        this.persister = persister;
        this.types = modules.stream()
            .map(WombatModule::type)
            .collect(Collectors.toMap(AssetType::name, Function.identity(), (first, _) -> first));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(
        responseCode = "200",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                properties = {
                    @SchemaProperty(name = "status", implementation = Status.class),
                    @SchemaProperty(name = "payload", implementation = Integer.class, description = "Number of staged metrics")
                }
            )
        )
    )
    public Response<Integer> push(@NotNull @Valid List<@NotNull @Valid MetricPush> pushes)
    {
        List<AssetType> resolved = pushes.stream().map(this::resolveType).toList();

        try {
            int staged = 0;
            for (int i = 0; i < pushes.size(); i++)
            {
                MetricPush push = pushes.get(i);
                this.persister.persist(push.asset(), resolved.get(i), List.copyOf(push.metrics()));
                staged += push.metrics().size();
            }
            return Response.success(staged);
        }
        catch (WombatPersistenceException e) {
            throw new InternalServerErrorException(e);
        }
    }

    private AssetType resolveType(MetricPush push)
    {
        AssetType type = this.types.get(push.assetType());
        if (type == null)
            throw new BadRequestException("Unknown asset type " + push.assetType() + " for asset " + push.asset().id());
        return type;
    }
}
