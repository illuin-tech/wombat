package tech.illuin.wombat.context.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class EnvironmentRepository implements PanacheRepositoryBase<EnvironmentEntity, String>
{
    public List<EnvironmentEntity> findActive()
    {
        return this.list("disabledAt is null");
    }
}
