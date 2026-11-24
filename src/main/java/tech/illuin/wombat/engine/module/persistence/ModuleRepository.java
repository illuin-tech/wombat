package tech.illuin.wombat.engine.module.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.core.asset.type.ModuleType;
import tech.illuin.wombat.persistence.backend.api.repository.Repository;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class ModuleRepository extends Repository<ModuleEntity, String>
{
    public ModuleRepository()
    {
        super(ModuleEntity.class);
    }

    /**
     * Inserts an enabled row for each of {@code types} that has none. The existing rows keep their state, and take the
     * asset-type as advertised now.
     *
     * @return the names of the types registered
     * @throws jakarta.persistence.PersistenceException if another instance registered one of them in the meantime
     */
    @Transactional
    public Set<String> register(Collection<ModuleType> types)
    {
        Map<String, ModuleEntity> existing = this.listAll().stream()
            .collect(Collectors.toMap(entity -> entity.typeName, Function.identity()));

        Set<String> registered = new HashSet<>();
        for (ModuleType type : types)
        {
            ModuleEntity entity = existing.get(type.name());
            if (entity == null)
            {
                this.entityManager.persist(ModuleEntity.registered(type));
                registered.add(type.name());
            }
            else if (!entity.type().equals(type))
                entity.advertise(type);
        }
        /* Flushed here, so that a concurrent registration surfaces as a PersistenceException rather than at commit */
        this.entityManager.flush();
        return registered;
    }
}
