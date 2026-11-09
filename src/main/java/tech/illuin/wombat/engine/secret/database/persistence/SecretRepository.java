package tech.illuin.wombat.engine.secret.database.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Query;
import tech.illuin.wombat.core.secret.SecretMetadata;

import java.util.List;

@ApplicationScoped
public class SecretRepository implements PanacheRepositoryBase<SecretEntity, String>
{
    public List<SecretMetadata> listMetadata()
    {
        return getEntityManager().createQuery(
            "SELECT new tech.illuin.wombat.core.secret.SecretMetadata(s.key, s.createdAt, s.updatedAt) "
                + "FROM SecretEntity s ORDER BY s.key",
            SecretMetadata.class
        ).getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<SecretEntity> findNeedingRewrap(String activeKekId, String afterKey, int limit)
    {
        String sql = "SELECT * FROM secrets WHERE kek_id <> ?1";
        if (afterKey != null)
            sql += " AND secret_key > ?2";
        sql += " ORDER BY secret_key";

        Query query = getEntityManager().createNativeQuery(sql, SecretEntity.class);
        query.setParameter(1, activeKekId);
        if (afterKey != null)
            query.setParameter(2, afterKey);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    public int updateWrappedDekIfRevisionMatches(String key, long revision, String kekId, byte[] wrappedDek, byte[] wrappingNonce)
    {
        return getEntityManager().createNativeQuery(
            "UPDATE secrets SET kek_id = ?1, wrapped_dek = ?2, wrapping_nonce = ?3, revision = revision + 1 "
                + "WHERE secret_key = ?4 AND revision = ?5"
        )
            .setParameter(1, kekId)
            .setParameter(2, wrappedDek)
            .setParameter(3, wrappingNonce)
            .setParameter(4, key)
            .setParameter(5, revision)
            .executeUpdate();
    }

    public long countNeedingRewrap(String activeKekId)
    {
        return count("kekId <> ?1", activeKekId);
    }
}
