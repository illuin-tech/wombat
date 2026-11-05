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

In dev mode, [`application-dev.yaml.dist`](../src/main/resources/application-dev.yaml.dist) makes `oidc` the parent of the `dev` profile (`quarkus.config.profile.parent`), so a plain `./mvnw quarkus:dev` requires signing in once its `"%oidc"` block points to your provider. Comment that line out to run dev mode without authentication.

Any property of `application-oidc.yaml` can be overridden the usual way, e.g. `QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH`.
The profile trusts `X-Forwarded-*` headers, so redirect URIs use the external `https` URL behind a TLS-terminating proxy or ingress. If Wombat is directly reachable by clients, disable this with `QUARKUS_HTTP_PROXY_PROXY_ADDRESS_FORWARDING=false`.

| Provider | `auth-server-url` | Notes |
|---|---|---|
| Keycloak | `https://<host>/realms/<realm>` | Roles: `QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH=realm_access/roles` and `QUARKUS_OIDC_ROLES_SOURCE=accesstoken` (realm roles are not in the ID token by default) |
| AWS Cognito | `https://cognito-idp.<region>.amazonaws.com/<user-pool-id>` | Roles: `QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH=cognito:groups` |
| Microsoft Entra ID | `https://login.microsoftonline.com/<tenant-id>/v2.0` | Roles: `roles` (app roles) or `groups` |
| Okta | `https://<org>.okta.com/oauth2/default` | Roles: `groups` (add a groups claim to the authorization server) |
| Google | `https://accounts.google.com` | No roles and no logout endpoint: map roles with emails or `"*"` and restrict with `AUTH_ALLOWED_EMAIL_DOMAINS`, set `QUARKUS_OIDC_AUTHENTICATION_USER_INFO_REQUIRED=true`. |

Register these URLs with your provider:
- redirect URI: `https://<wombat-host>/auth/callback`
- post-logout redirect URI: `https://<wombat-host>/logout/done`

Pure OAuth2 providers such as GitHub are not OpenID Connect and are not supported.

## Roles and permissions

Every user needs a Wombat role, mapped from the roles or groups their provider puts in the role claim (`QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH`, see the table above). Users with no mapped role get `403`.

```sh
AUTH_ROLES_ADMIN=wombat-admins,alice@example.com   # comma-separated provider roles or emails granting "admin"
AUTH_ROLES_USER=wombat-users                       # "*" grants the role to every authenticated user
```

An entry containing `@` matches the user's email (`AUTH_EMAIL_CLAIM`, case-insensitive) instead of a provider role. This is how to give roles to specific people with providers that send no roles, such as Google. The email only counts when the provider flags it verified (`email_verified`), unless `AUTH_REQUIRE_VERIFIED_EMAIL=false`; Entra ID sends no such claim, so use app roles there.

Endpoints check permissions, and each role grants a fixed set of them:

| Permission | admin | user |
|---|---|---|
| `ui.dashboard.read` | ✓ | ✓ |
| `ui.apikeys.manage` | ✓ | |
| `ui.users.read` | ✓ | |
| `api.impact.read` | ✓ | ✓ |
| `api.simulation.read` | ✓ | ✓ |
| `api.environment.read` | ✓ | ✓ |
| `api.apikey.manage` | ✓ | |
| `api.user.manage` | ✓ | |

`AUTH_ROLES_ALLOWED` is gone: replace `AUTH_ROLES_ALLOWED=wombat-users` with `AUTH_ROLES_USER=wombat-users`, or `AUTH_ROLES_USER=*` if every authenticated user had access.

When authentication is disabled (no `oidc` profile), every caller holds every permission.

Wombat records every signed-in user from their requests (at most every 5 minutes, and whenever their roles change). Admins see them on the **Users** page (`/users`, in the user menu), and can grant them roles there, for example to promote someone to admin. A user holds the roles mapped above plus the ones granted in Wombat, from their next request. Roles from the provider or `AUTH_ROLES_*` show a lock and can only be removed at their source; granted ones can be removed from the page, except an admin's own admin role. The API behind it is `PUT` and `DELETE /api/users/{user-id}/roles/{role}` (`api.user.manage`).

Granting needs someone to have signed in once, and the first admin must come from the provider or `AUTH_ROLES_ADMIN`.

## Access rules

Use the `auth` rules to restrict access further; when several are set, all of them must pass:

```sh
AUTH_ALLOWED_EMAIL_DOMAINS=example.com       # comma-separated
AUTH_EMAIL_CLAIM=email                       # claim checked against the allowed domains
AUTH_REQUIRE_VERIFIED_EMAIL=true             # requires the email_verified claim to be true (also for emails in AUTH_ROLES_*)
AUTH_DISPLAY_NAME_CLAIMS=email,name,preferred_username  # first claim found is shown in the header
AUTH_IDP_LOGOUT=true                         # also ends the provider session when it exposes an end_session_endpoint
```

Unauthenticated browsers are redirected to the provider, and unauthenticated REST calls get `401`. Authenticated callers that fail a rule get `403`.

`/q/health`, `/q/metrics`, `/logout` and static assets under `/public/` stay public. Other paths are protected by the `quarkus.http.auth.permission` entries in [`application-oidc.yaml`](../src/main/resources/application-oidc.yaml).

## API keys

Admins create API keys for scripts and CI. A key belongs to no one: it carries its own `api.*` read permissions (never `ui.*` or `api.apikey.manage`), works for whoever holds it until it expires or is revoked, and is sent in the `X-API-Key` header. Only admins can create, list and revoke keys, from the **API keys** page (`/api-keys`, in the user menu) or the API:

```sh
curl -X POST https://<wombat-host>/api/keys -H 'Content-Type: application/json' --cookie <session> \
  -d '{"name": "ci", "permissions": ["api.impact.read"], "expires-at": "2027-01-30T00:00:00Z"}'
curl https://<wombat-host>/api/impact/environments -H 'X-API-Key: <key>'
```

The key is returned once: Wombat only stores its SHA-256 hash, so a lost key cannot be recovered, only revoked and replaced. `GET /api/keys` lists every key with the admin who created it, and `DELETE /api/keys/{id}` revokes one.

Keys are opaque random strings starting with `wmb_` (256 random bits), not JWTs: there is no signing key to configure, and keys survive restarts. The prefix lets secret scanners spot leaked keys. A key sent as `Authorization: Bearer` is rejected: that header is left to the identity provider.

```sh
AUTH_API_KEYS_MAX_LIFETIME=90d             # longest expiry a key can be given
AUTH_API_KEYS_ALLOW_NEVER_EXPIRING=false   # true lets keys be created without "expires-at"
```

Keys work the same with every provider: Wombat alone decides whether a key is valid, and never calls the provider. A key keeps working when the admin who created it leaves or is demoted, so revoke keys that are no longer needed, and prefer keys that expire.

## Logout

`/logout` clears the Wombat session. If the provider advertises an `end_session_endpoint` (Keycloak, Entra ID, Okta, Auth0, ...), Wombat also ends the provider session and the provider sends the user back to `/logout/done`. Providers without that endpoint (Google) only get the Wombat session cleared.
