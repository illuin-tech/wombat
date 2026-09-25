package tech.illuin.wombat.persistence.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import tech.illuin.wombat.core.source.data.LLMData;

@Entity
@Table(name = "model_metrics")
public class LLMMetricEntity extends PanacheEntityBase
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INTEGER")
    public Long id;

    @Column(name = "instant_ms", nullable = false, columnDefinition = "INTEGER")
    public long instantMs;

    @Convert(converter = LLMDataConverter.class)
    @Column(nullable = false)
    public LLMData data;

    @Column(name = "output_tokens", nullable = false, columnDefinition = "INTEGER")
    public long outputTokens;

    @Convert(converter = CompactionFlagConverter.class)
    @Column(name = "compacted", nullable = false, columnDefinition = "INTEGER")
    public boolean compacted;
}
