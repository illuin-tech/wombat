package tech.illuin.wombat.persistence.backend.push;

import feign.Headers;
import feign.RequestLine;
import tech.illuin.wombat.persistence.push.MetricPush;

import java.util.List;

public interface WombatPushClient
{
    @RequestLine("POST /api/metrics")
    @Headers("Content-Type: application/json")
    void push(List<MetricPush> metrics);
}
