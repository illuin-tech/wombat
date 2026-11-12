package tech.illuin.wombat.endpoint;

import io.quarkus.security.PermissionsAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.source.persistence.WombatMetricPersister;
import tech.illuin.wombat.core.source.persistence.WombatPersistenceException;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tech.illuin.wombat.feature.Feature;
import tech.illuin.wombat.feature.RequiresFeature;
import tech.illuin.wombat.persistence.push.MetricPush;
import tech.illuin.wombat.security.Permission;

import java.util.List;

/**
 * Receives metrics sampled by workers, see {@link tech.illuin.wombat.persistence.backend.push.PushBackendConfig}. Served
 * with the {@link Feature#API_WRITE}, by an aggregator by default. Metrics are staged exactly like those of local sources, and land in the
 * database with the next sampling window.
 */
@Path("api/metrics")
@RequiresFeature(Feature.API_WRITE)
@PermissionsAllowed(Permission.Names.API_METRIC_WRITE)
public class MetricController
{
    private final WombatMetricPersister persister;
    private final ModuleRegistry modules;

    public MetricController(
        WombatMetricPersister persister,
        ModuleRegistry modules
    ) {
        this.persister = persister;
        this.modules = modules;
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
        return this.modules.getModule(push.assetType())
            .filter(ModuleMetadata::enabled)
            .map(ModuleMetadata::type)
            .orElseThrow(() -> new BadRequestException("No active module for asset type " + push.assetType() + " of asset " + push.asset().id()));
    }
}
