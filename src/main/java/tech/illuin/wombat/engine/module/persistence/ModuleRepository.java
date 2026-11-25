package tech.illuin.wombat.engine.module.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.core.asset.type.ModuleType;
import tech.illuin.wombat.persistence.backend.api.repository.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ModuleRepository extends Repository<ModuleEntity, String>
{
    public ModuleRepository()
    {
        super(ModuleEntity.class);
    }

    /**
     * Inserts an enabled row for each of {@code types} that has none. The existing rows keep their state, and take the
     * asset-type as advertised now. A module with no ordering yet, new or not, is placed last, in the order of
     * {@code types}.
     *
     * @return the names of the types registered
     * @throws jakarta.persistence.PersistenceException if another instance registered one of them in the meantime
     */
    @Transactional
    public Set<String> register(List<ModuleType> types)
    {
        Map<String, ModuleEntity> rows = new HashMap<>();
        this.listAll().forEach(entity -> rows.put(entity.typeName, entity));

        Set<String> registered = new HashSet<>();
        for (ModuleType type : types)
        {
            ModuleEntity entity = rows.get(type.name());
            if (entity == null)
            {
                entity = ModuleEntity.registered(type);
                this.entityManager.persist(entity);
                rows.put(type.name(), entity);
                registered.add(type.name());
            }
            else if (!entity.type().equals(type))
                entity.advertise(type);

            if (entity.ordering == null)
                entity.ordering = lastOrdering(rows.values()) + 1;
        }
        /* Flushed here, so that a concurrent registration surfaces as a PersistenceException rather than at commit */
        this.entityManager.flush();
        return registered;
    }

    private static int lastOrdering(Collection<ModuleEntity> rows)
    {
        return rows.stream()
            .filter(row -> row.ordering != null)
            .mapToInt(row -> row.ordering)
            .max()
            .orElse(0);
    }
}
