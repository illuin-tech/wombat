package tech.illuin.wombat.security.apikey;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

public class ApiKeyAuthenticationRequest extends BaseAuthenticationRequest
{
    private final String token;

    public ApiKeyAuthenticationRequest(String token)
    {
        this.token = token;
    }

    public String token()
    {
        return this.token;
    }
}
