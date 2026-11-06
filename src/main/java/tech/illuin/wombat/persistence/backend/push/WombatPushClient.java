package tech.illuin.wombat.persistence.backend.push;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import tech.illuin.wombat.core.source.data.MetricData;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.persistence.backend.api.store.MetricSample;

import java.util.List;

public interface WombatPushClient
{
    @RequestLine("POST /api/workers/{worker}/metrics")
    @Headers("Content-Type: application/json")
    Response<Integer> push(@Param("worker") String worker, List<MetricSample<MetricData>> samples);
}
