package tech.illuin.wombat.security.user;

import jakarta.enterprise.context.ApplicationScoped;
import tech.illuin.wombat.persistence.backend.api.repository.Repository;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class RoleGrantRepository extends Repository<RoleGrantEntity, String>
{
    public RoleGrantRepository()
    {
        super(RoleGrantEntity.class);
    }

    public Optional<RoleGrantEntity> findByUserAndRole(String userId, String role)
    {
        List<RoleGrantEntity> results = getEntityManager()
            .createQuery("FROM RoleGrantEntity WHERE userId = :userId AND role = :role", RoleGrantEntity.class)
            .setParameter("userId", userId)
            .setParameter("role", role)
            .setMaxResults(1)
            .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** Every grant with its user's identity: rows of {@code [issuer, subject, role]}. */
    public List<Object[]> findAllWithIdentity()
    {
        return getEntityManager()
            .createQuery("select u.issuer, u.subject, g.role from RoleGrantEntity g, UserEntity u where u.id = g.userId", Object[].class)
            .getResultList();
    }
}
