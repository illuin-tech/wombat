package tech.illuin.wombat.persistence.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import tech.illuin.wombat.core.source.data.KubernetesData;

@Entity
@Table(name = "server_metrics")
public class KubernetesMetricEntity extends PanacheEntityBase
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INTEGER")
    public Long id;

    @Column(name = "instant_ms", nullable = false, columnDefinition = "INTEGER")
    public long instantMs;

    @Convert(converter = KubernetesDataConverter.class)
    @Column(nullable = false)
    public KubernetesData data;

    @Column(name = "cpu_nanocores", nullable = false, columnDefinition = "REAL")
    public double cpuNanocores;

    @Column(name = "ram_bytes", nullable = false, columnDefinition = "REAL")
    public double ramBytes;
}
