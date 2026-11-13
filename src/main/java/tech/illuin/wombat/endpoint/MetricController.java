package tech.illuin.wombat.endpoint;

import io.quarkus.resteasy.reactive.server.EndpointDisabled;
import io.quarkus.security.PermissionsAllowed;
import jakarta.transaction.Transactional;
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
import tech.illuin.wombat.commons.NodeMode;
import tech.illuin.wombat.commons.NodeModeConfigSourceFactory;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.source.data.MetricData;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tech.illuin.wombat.persistence.backend.push.PushBackendConfig;
import tech.illuin.wombat.persistence.backend.api.store.MetricSample;
import tech.illuin.wombat.persistence.backend.api.store.MetricStoreContainer;
import tech.illuin.wombat.persistence.backend.api.store.SkippedMetrics;
import tech.illuin.wombat.security.Permission;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Receives metrics sampled by workers, see {@link tech.illuin.wombat.persistence.backend.push.PushBackendConfig}. Only
 * an {@link NodeMode#AGGREGATOR} serves it. Each worker already aggregated its metrics over its sampling window, so they
 * are saved to the database as they are, at the start of the worker's window, for the asset {@code workerId.assetId}, see
 * {@link WorkerController}. A sample for an asset type without an active module, or of a service family without a store,
 * is skipped and reported in the response status, under {@code errors}, with the asset id the worker knows; the others
 * are saved.
 */
@Path("api/workers/{worker}/metrics")
@EndpointDisabled(name = NodeModeConfigSourceFactory.AGGREGATOR, stringValue = "false")
@PermissionsAllowed(Permission.Names.API_METRIC_WRITE)
public class MetricController
{
    private final MetricStoreContainer stores;
    private final ModuleRegistry modules;

    private static final Logger logger = LoggerFactory.getLogger(MetricController.class);

    public MetricController(
        MetricStoreContainer stores,
        ModuleRegistry modules
    ) {
        this.stores = stores;
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
                    @SchemaProperty(name = "status", implementation = Status.class, description = "Lists the skipped metrics under metadata.errors, if any"),
                    @SchemaProperty(name = "payload", implementation = Integer.class, description = "Number of saved metrics")
                }
            )
        )
    )
    @Transactional
    public Response<Integer> push(
        @PathParam("worker") @Pattern(regexp = PushBackendConfig.WORKER_ID_PATTERN) String worker,
        @NotNull @Valid List<@NotNull @Valid MetricSample<MetricData>> samples
    ) {
        List<MetricSample<MetricData>> accepted = new ArrayList<>();
        Map<SkipKey, Integer> skipped = new LinkedHashMap<>();

        for (MetricSample<MetricData> sample : samples)
        {
            if (!this.hasActiveModule(sample.asset()))
            {
                skipped.merge(new SkipKey(sample.asset(), "No active module for asset type " + sample.asset().assetType()), 1, Integer::sum);
                continue;
            }

            if (!this.stores.supports(sample.data().serviceFamily()))
            {
                skipped.merge(new SkipKey(sample.asset(), "No store for service family " + sample.data().serviceFamily()), 1, Integer::sum);
                continue;
            }

            AssetIdentity asset = sample.asset();
            AssetIdentity owned = new AssetIdentity(worker + "." + asset.id(), asset.environmentId(), asset.name(), asset.assetType());
            accepted.add(new MetricSample<>(owned, sample.window(), sample.data()));
        }

        this.stores.save(accepted);

        int saved = accepted.size();
        if (skipped.isEmpty())
            return Response.success(saved);

        List<SkippedMetrics> errors = skipped.entrySet().stream()
            .map(entry -> new SkippedMetrics(entry.getKey().asset().id(), entry.getKey().asset().assetType(), entry.getKey().reason(), entry.getValue()))
            .toList();
        errors.forEach(error -> logger.trace("Skipped {} metric(s) pushed for asset {}: {}", error.count(), error.assetId(), error.reason()));
        return Response.success("Saved " + saved + " metric(s), skipped " + (samples.size() - saved), saved).set(SkippedMetrics.STATUS_KEY, errors);
    }

    private boolean hasActiveModule(AssetIdentity asset)
    {
        return this.modules.getModule(asset.assetType())
            .filter(ModuleMetadata::enabled)
            .isPresent();
    }

    private record SkipKey(AssetIdentity asset, String reason) {}
}
