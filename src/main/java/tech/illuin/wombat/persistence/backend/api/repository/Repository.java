package tech.illuin.wombat.persistence.backend.api.repository;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

public abstract class Repository<Entity, Id>
{
    @Inject
    protected EntityManager entityManager;

    private final Class<Entity> entityClass;

    protected Repository(Class<Entity> entityClass)
    {
        this.entityClass = entityClass;
    }

    protected EntityManager getEntityManager()
    {
        return this.entityManager;
    }

    public boolean isPersistent(Entity entity)
    {
        return this.entityManager.contains(entity);
    }

    @Transactional
    public void persist(Entity entity)
    {
        this.entityManager.persist(entity);
    }

    @Transactional
    public void persist(Iterable<Entity> entities)
    {
        for (Entity entity : entities)
            this.entityManager.persist(entity);
    }

    @Transactional
    public Entity findById(Id id)
    {
        return this.entityManager.find(this.entityClass, id);
    }

    @Transactional
    public Optional<Entity> findByIdOptional(Id id)
    {
        return Optional.ofNullable(this.findById(id));
    }

    @Transactional
    public List<Entity> listAll()
    {
        return this.entityManager
            .createQuery("FROM " + this.entityClass.getSimpleName(), this.entityClass)
            .getResultList();
    }

    @Transactional
    public void delete(Entity entity)
    {
        Entity managed = this.entityManager.contains(entity) ? entity : this.entityManager.merge(entity);
        this.entityManager.remove(managed);
    }

    @Transactional
    public void deleteById(Id id)
    {
        String idAttribute = this.entityManager.getMetamodel()
            .entity(this.entityClass)
            .getId(Object.class)
            .getName();

        //noinspection SqlSourceToSinkFlow
        this.entityManager
            .createQuery("DELETE FROM " + this.entityClass.getSimpleName() + " WHERE " + idAttribute + " = :id")
            .setParameter("id", id)
            .executeUpdate();
    }

    @Transactional
    public void deleteAll()
    {
        this.entityManager
            .createQuery("DELETE FROM " + this.entityClass.getSimpleName())
            .executeUpdate();
    }
}
