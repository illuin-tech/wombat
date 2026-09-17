package tech.illuin.wombat.impact.llm;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.core.evaluation.impact.llm.LLMMetricResolver;
import tech.illuin.wombat.persistence.dialect.JsonPathDialect;
import tech.illuin.wombat.persistence.model.LLMMetricEntity;

@ApplicationScoped
public class LLMModelMetricRepository implements PanacheRepositoryBase<LLMMetricEntity, Long>, LLMMetricResolver
{
    private final JsonPathDialect dialect;

    public LLMModelMetricRepository(JsonPathDialect dialect)
    {
        this.dialect = dialect;
    }

    @Transactional
    public void save(LLMMetricEntity entity)
    {
        persist(entity);
    }

    @Override
    public long sumOutputTokens(long startMs, long endMs, String assetId)
    {
        Query query = getEntityManager().createNativeQuery(
            "SELECT COALESCE(SUM(output_tokens), 0) FROM model_metrics"
            + " WHERE instant_ms >= :start AND instant_ms <= :end"
            + " AND " + this.dialect.text("data", "assetId") + " = :assetId");
        query.setParameter("start", startMs);
        query.setParameter("end", endMs);
        query.setParameter("assetId", assetId);
        return ((Number) query.getSingleResult()).longValue();
    }
}
