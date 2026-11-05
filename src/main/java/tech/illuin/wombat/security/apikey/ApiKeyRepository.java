package tech.illuin.wombat.security.apikey;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ApiKeyRepository implements PanacheRepositoryBase<ApiKeyEntity, String>
{
    public Optional<ApiKeyEntity> findByHash(String keyHash)
    {
        return this.find("keyHash", keyHash).firstResultOptional();
    }

    public List<ApiKeyEntity> findAllNewestFirst()
    {
        return this.list("order by createdAt desc");
    }

    public List<ApiKeyEntity> findByOwnerNewestFirst(String ownerId)
    {
        return this.list("ownerId = ?1 order by createdAt desc", ownerId);
    }
}
