package tech.illuin.wombat.security.user;

import jakarta.enterprise.context.ApplicationScoped;
import tech.illuin.wombat.persistence.backend.api.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class UserRepository extends Repository<UserEntity, String>
{
    public UserRepository()
    {
        super(UserEntity.class);
    }

    public Optional<UserEntity> findByIdentity(String issuer, String subject)
    {
        List<UserEntity> results = getEntityManager()
            .createQuery("FROM UserEntity WHERE issuer = :issuer AND subject = :subject", UserEntity.class)
            .setParameter("issuer", issuer)
            .setParameter("subject", subject)
            .setMaxResults(1)
            .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<UserEntity> findAllByName()
    {
        return getEntityManager()
            .createQuery("FROM UserEntity ORDER BY LOWER(COALESCE(name, subject))", UserEntity.class)
            .getResultList();
    }

    public List<UserEntity> findByIds(Collection<String> ids)
    {
        return getEntityManager()
            .createQuery("FROM UserEntity WHERE id IN :ids", UserEntity.class)
            .setParameter("ids", ids)
            .getResultList();
    }
}
