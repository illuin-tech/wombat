package tech.illuin.wombat.endpoint;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.engine.context.model.AssetHistoryInfo;
import tech.illuin.wombat.engine.context.model.EnvironmentInfo;
import tech.illuin.wombat.engine.context.persistence.AssetConfigHistoryRepository;
import tech.illuin.wombat.engine.context.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.persistence.EnvironmentEntity;
import tech.illuin.wombat.engine.context.persistence.EnvironmentRepository;

import java.util.List;

/**
 * Read-only view of the persisted environment configuration. The monitored-environments YAML is the
 * source of truth and is reconciled into the DB at startup (see {@link tech.illuin.wombat.engine.context.EnvironmentReconciler}), so
 * this controller exposes the current state and the per-asset change history but never mutates config.
 */
@Path("api/environments")
public class EnvironmentController
{
    private final EnvironmentRepository repository;
    private final AssetRepository assetRepository;
    private final AssetConfigHistoryRepository historyRepository;

    public EnvironmentController(EnvironmentRepository repository, AssetRepository assetRepository, AssetConfigHistoryRepository historyRepository)
    {
        this.repository = repository;
        this.assetRepository = assetRepository;
        this.historyRepository = historyRepository;
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
                    @SchemaProperty(name = "payload", type = SchemaType.ARRAY, implementation = EnvironmentInfo.class)
                }
            )
        )
    )
    public Response<List<EnvironmentInfo>> list()
    {
        return Response.success(
            this.repository.listAll().stream()
                .map(this::toInfo)
                .toList()
        );
    }

    @GET
    @Path("/{id}/assets/{assetId}/history")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(
        responseCode = "200",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                properties = {
                    @SchemaProperty(name = "status", implementation = Status.class),
                    @SchemaProperty(name = "payload", type = SchemaType.ARRAY, implementation = AssetHistoryInfo.class)
                }
            )
        )
    )
    public Response<List<AssetHistoryInfo>> history(@PathParam("id") String id, @PathParam("assetId") String assetId)
    {
        this.find(id);
        return Response.success(
            this.historyRepository.findByAsset(assetId).stream()
                .map(AssetHistoryInfo::from)
                .toList()
        );
    }

    private EnvironmentInfo toInfo(EnvironmentEntity entity)
    {
        return EnvironmentInfo.from(entity, this.assetRepository.findByEnvironment(entity.id));
    }

    private EnvironmentEntity find(String id)
    {
        try {
            return this.repository.findByIdOptional(id).orElseThrow(() -> new UnknownEnvironmentException(id));
        }
        catch (UnknownEnvironmentException e) {
            throw new NotFoundException(e);
        }
    }
}
