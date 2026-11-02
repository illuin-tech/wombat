package tech.illuin.wombat.security.user;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<UserEntity, String>
{
    public Optional<UserEntity> findByIdentity(String issuer, String subject)
    {
        return this.find("issuer = ?1 and subject = ?2", issuer, subject).firstResultOptional();
    }

    public List<UserEntity> findAllByName()
    {
        return this.list("order by lower(coalesce(name, subject))");
    }
}
