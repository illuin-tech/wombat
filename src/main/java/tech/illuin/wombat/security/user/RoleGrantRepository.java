package tech.illuin.wombat.security.user;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class RoleGrantRepository implements PanacheRepositoryBase<RoleGrantEntity, String>
{
    public Optional<RoleGrantEntity> findByUserAndRole(String userId, String role)
    {
        return this.find("userId = ?1 and role = ?2", userId, role).firstResultOptional();
    }

    /** Every grant with its user's identity: rows of {@code [issuer, subject, role]}. */
    public List<Object[]> findAllWithIdentity()
    {
        return this.getEntityManager()
            .createQuery("select u.issuer, u.subject, g.role from RoleGrantEntity g, UserEntity u where u.id = g.userId", Object[].class)
            .getResultList();
    }
}
