package tech.illuin.wombat.commons;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import tech.illuin.wombat.engine.connector.boavizta.BoaviztaProperties;
import tech.illuin.wombat.engine.connector.ecologits.EcologitsProperties;
import tech.illuin.wombat.engine.evaluation.CachedAssetEvaluatorProperties;
import tech.illuin.wombat.engine.module.extension.WombatModuleProperties;
import tech.illuin.wombat.engine.monitor.MonitorProperties;
import tech.illuin.wombat.persistence.backend.push.WorkerProperties;

@ConfigMapping(prefix = "wombat")
public interface WombatProperties
{
    @WithDefault("standalone")
    NodeMode mode();

    WorkerProperties worker();

    MonitorProperties monitor();

    EvaluatorProperties evaluator();

    WombatModuleProperties module();

    ConnectorProperties connector();

    interface EvaluatorProperties
    {
        CachedAssetEvaluatorProperties cache();
    }

    interface ConnectorProperties
    {
        BoaviztaProperties boavizta();

        EcologitsProperties ecologits();
    }
}
