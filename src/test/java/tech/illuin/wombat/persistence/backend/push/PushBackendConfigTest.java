package tech.illuin.wombat.persistence.backend.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PushBackendConfigTest
{
    @Test
    void createClient_acceptsAProxyWithAHostAndAPort()
    {
        assertNotNull(PushBackendConfig.createClient(WombatPushClient.class, properties("http://proxy.example.com:3128"), new ObjectMapper()));
    }

    @Test
    void createClient_rejectsAProxyWithoutAPort()
    {
        assertThrows(IllegalArgumentException.class, () -> PushBackendConfig.createClient(WombatPushClient.class, properties("http://proxy.example.com"), new ObjectMapper()));
    }

    private static AggregatorProperties properties(String proxyUrl)
    {
        return new AggregatorProperties()
        {
            @Override
            public String url()
            {
                return "http://aggregator.example.com";
            }

            @Override
            public String apiKey()
            {
                return "key";
            }

            @Override
            public Optional<String> proxyUrl()
            {
                return Optional.of(proxyUrl);
            }

            @Override
            public Optional<Duration> timeout()
            {
                return Optional.empty();
            }
        };
    }
}
