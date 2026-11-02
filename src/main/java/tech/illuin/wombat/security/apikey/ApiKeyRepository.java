package tech.illuin.wombat.security.apikey;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ApiKeyRepository implements PanacheRepositoryBase<ApiKeyEntity, String>
{
    public List<ApiKeyEntity> findAllNewestFirst()
    {
        return this.list("order by createdAt desc");
    }
}
