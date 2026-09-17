package tech.illuin.wombat.context.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class AssetRepository implements PanacheRepositoryBase<AssetEntity, String>
{
    public List<AssetEntity> findByEnvironment(String environmentId)
    {
        return this.list("environmentId = ?1 and deletedAt is null", environmentId);
    }
}
