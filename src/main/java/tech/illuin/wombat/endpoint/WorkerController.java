package tech.illuin.wombat.endpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.resteasy.reactive.server.EndpointDisabled;
import io.quarkus.security.PermissionsAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.commons.NodeModeConfigSourceFactory;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.source.Monitorable;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.engine.context.EnvironmentConflictException;
import tech.illuin.wombat.engine.context.EnvironmentReconciler;
import tech.illuin.wombat.engine.context.model.UnrecognizedAsset;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tech.illuin.wombat.engine.monitor.MonitoredEnvironments;
import tech.illuin.wombat.persistence.backend.push.PushBackendConfig;
import tech.illuin.wombat.security.Permission;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Receives the environments declared by workers, so an aggregator evaluates their assets without declaring them itself.
 * Each worker owns the assets it declares, see {@link EnvironmentReconciler}, identified as {@code workerId.assetId}, so
 * two workers declaring the same asset id declare two assets. Only an {@link NodeMode#AGGREGATOR} serves it.
 */
@Path("api/workers")
@EndpointDisabled(name = NodeModeConfigSourceFactory.AGGREGATOR_CONFIG_KEY, stringValue = "false")
public class WorkerController
{
    private final EnvironmentReconciler reconciler;
    private final ModuleRegistry modules;
    private final JsonMapper assetMapper;

    private static final Logger logger = LoggerFactory.getLogger(WorkerController.class);

    public WorkerController(
        EnvironmentReconciler reconciler,
        ModuleRegistry modules,
        JsonMapper assetMapper
    ) {
        this.reconciler = reconciler;
        this.modules = modules;
        this.assetMapper = assetMapper;
    }

    /**
     * Replaces the environments declared by a worker: its assets missing from the request are deleted. The environments are
     * shared with the other workers and the aggregator, the assets are not.
     */
    @PUT
    @Path("/{worker}/environments")
    @PermissionsAllowed(Permission.Names.API_ENVIRONMENT_WRITE)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(
        responseCode = "200",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                properties = {
                    @SchemaProperty(name = "status", implementation = Status.class),
                    @SchemaProperty(name = "payload", implementation = EnvironmentReconciler.Result.class)
                }
            )
        )
    )
    @APIResponse(responseCode = "409", description = "An asset is already declared by the aggregator, under the same workerId.assetId")
    public Response<EnvironmentReconciler.Result> declare(
        @PathParam("worker") @Pattern(regexp = PushBackendConfig.WORKER_ID_PATTERN) String worker,
        @NotNull @Valid MonitoredEnvironments environments
    ) {
        for (Asset asset : environments.allAssets())
            this.validate(asset);

        try {
            return Response.success(this.reconciler.reconcile(worker, this.qualify(worker, environments)));
        }
        catch (EnvironmentConflictException e) {
            throw new ClientErrorException(e.getMessage(), jakarta.ws.rs.core.Response.Status.CONFLICT);
        }
    }

    private Map<String, Environment> qualify(String worker, MonitoredEnvironments environments)
    {
        Map<String, Environment> qualified = new LinkedHashMap<>();
        environments.environments().forEach((id, environment) -> qualified.put(
            id, new Environment(environment.id(), environment.assets().stream().map(asset -> this.qualify(worker, asset)).toList())
        ));
        return qualified;
    }

    /** Through JSON, since an asset only takes its identity when created. */
    private Asset qualify(String worker, Asset asset)
    {
        ObjectNode node = this.assetMapper.valueToTree(asset);
        node.put("id", worker + "." + asset.identity().id());
        try {
            return this.assetMapper.treeToValue(node, Asset.class);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to qualify asset " + asset.identity().id() + " of worker " + worker, e);
        }
    }

    /**
     * This instance evaluates the asset, so it needs its module, and a worker only declares what it samples. A module
     * disabled here does not reject the asset: its metrics are skipped when pushed, see {@link MetricController}. The
     * asset's secrets are not checked: those of its source are resolved on the worker, and this instance may not know
     * them.
     */
    private void validate(Asset asset)
    {
        if (asset instanceof UnrecognizedAsset unrecognized)
            throw new BadRequestException("Unknown asset type " + unrecognized.rawType() + " for asset " + asset.identity().id());

        ModuleMetadata module = this.modules.getModule(asset.type()).orElseThrow(() -> new BadRequestException("No module for asset type " + asset.type().name() + " of asset " + asset.identity().id()));
        if (!module.enabled())
            logger.debug("Asset {} is declared with asset type {} whose module is disabled, its metrics will be skipped", asset.identity().id(), asset.type().name());

        if (!(asset instanceof Monitorable))
            throw new BadRequestException("Asset " + asset.identity().id() + " of type " + asset.type().name() + " is not monitorable, declare it in the aggregator's environments file");
    }
}
