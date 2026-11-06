package tech.illuin.wombat.persistence.backend.push;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import tech.illuin.wombat.engine.monitor.MonitoredEnvironments;

public interface WombatEnvironmentClient
{
    @RequestLine("PUT /api/workers/{worker}/environments")
    @Headers("Content-Type: application/json")
    void declare(@Param("worker") String worker, MonitoredEnvironments environments);
}
