# Access Control

Wombat can optionally restrict access to the UI and REST endpoints using OpenID Connect. It works with any compliant provider (Keycloak, Microsoft Entra ID, Okta, Auth0, Google, ...). Authentication is disabled by default and enabled by the `oidc` profile.

## Provider

All shared OIDC settings live in [`application-oidc.yaml`](../src/main/resources/application-oidc.yaml): protected paths, scopes, callback path, proxy headers. Enable them with the `oidc` profile and set only what is specific to your provider:

```sh
QUARKUS_PROFILE=prod,oidc
QUARKUS_OIDC_AUTH_SERVER_URL=https://your-idp.example.com   # issuer URL
QUARKUS_OIDC_CLIENT_ID=<client-id>
QUARKUS_OIDC_CREDENTIALS_SECRET=<client-secret>
```

In dev mode, use `./mvnw quarkus:dev -Dquarkus.profile=dev,oidc` (see [`application-dev.yaml.dist`](../src/main/resources/application-dev.yaml.dist)).

Any property of `application-oidc.yaml` can be overridden the usual way, e.g. `QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH`.
The profile trusts `X-Forwarded-*` headers, so redirect URIs use the external `https` URL behind a TLS-terminating proxy or ingress. If Wombat is directly reachable by clients, disable this with `QUARKUS_HTTP_PROXY_PROXY_ADDRESS_FORWARDING=false`.

| Provider | `auth-server-url` | Notes |
|---|---|---|
| Keycloak | `https://<host>/realms/<realm>` | Roles: `QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH=realm_access/roles` |
| Microsoft Entra ID | `https://login.microsoftonline.com/<tenant-id>/v2.0` | Roles: `roles` (app roles) or `groups` |
| Okta | `https://<org>.okta.com/oauth2/default` | Roles: `groups` (add a groups claim to the authorization server) |
| Google | `https://accounts.google.com` | No roles and no logout endpoint: use `AUTH_ALLOWED_EMAIL_DOMAINS`, and set `QUARKUS_OIDC_AUTHENTICATION_USER_INFO_REQUIRED=true` |

Register these URLs with your provider:
- redirect URI: `https://<wombat-host>/auth/callback`
- post-logout redirect URI: `https://<wombat-host>/logout/done`

Pure OAuth2 providers such as GitHub are not OpenID Connect and are not supported.

## Access rules

By default every user the provider authenticates gets access. Use the `auth` rules to restrict access; when several are set, all of them must pass:

```sh
AUTH_ROLES_ALLOWED=wombat-users              # needs QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH
AUTH_ALLOWED_EMAIL_DOMAINS=example.com       # comma-separated
AUTH_EMAIL_CLAIM=email                       # claim checked against the allowed domains
AUTH_REQUIRE_VERIFIED_EMAIL=true             # requires the email_verified claim to be true
AUTH_DISPLAY_NAME_CLAIMS=email,name,preferred_username  # first claim found is shown in the header
AUTH_IDP_LOGOUT=true                         # also ends the provider session when it exposes an end_session_endpoint
```

Unauthenticated browsers are redirected to the provider, and unauthenticated REST calls get `401`. Authenticated callers that fail a rule get `403`.

`/q/health`, `/q/metrics`, `/logout` and static assets under `/public/` stay public. Other paths are protected by the `quarkus.http.auth.permission` entries in [`application-oidc.yaml`](../src/main/resources/application-oidc.yaml).

## Logout

`/logout` clears the Wombat session. If the provider advertises an `end_session_endpoint` (Keycloak, Entra ID, Okta, Auth0, ...), Wombat also ends the provider session and the provider sends the user back to `/logout/done`. Providers without that endpoint (Google) only get the Wombat session cleared.
