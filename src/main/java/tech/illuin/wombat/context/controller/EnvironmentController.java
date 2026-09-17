package tech.illuin.wombat.context.controller;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import tech.illuin.wombat.impact.controller.UnknownEnvironmentException;
import tech.illuin.wombat.context.model.AssetHistoryInfo;
import tech.illuin.wombat.context.model.EnvironmentInfo;
import tech.illuin.wombat.context.persistence.AssetConfigHistoryRepository;
import tech.illuin.wombat.context.persistence.AssetRepository;
import tech.illuin.wombat.context.persistence.EnvironmentEntity;
import tech.illuin.wombat.context.persistence.EnvironmentRepository;

import java.util.List;

/**
 * Read-only view of the persisted environment configuration. The monitored-environments YAML is the
 * source of truth and is reconciled into the DB at startup (see {@link tech.illuin.wombat.context.EnvironmentReconciler}), so
 * this controller exposes the current state and the per-asset change history but never mutates config.
 */
@Path("environments")
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
    public List<EnvironmentInfo> list()
    {
        return this.repository.listAll().stream().map(this::toInfo).toList();
    }

    @GET
    @Path("/{id}/assets/{assetId}/history")
    @Produces(MediaType.APPLICATION_JSON)
    public List<AssetHistoryInfo> history(@PathParam("id") String id, @PathParam("assetId") String assetId)
    {
        this.find(id);
        return this.historyRepository.findByAsset(assetId).stream().map(AssetHistoryInfo::from).toList();
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
