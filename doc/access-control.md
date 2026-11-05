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

| Provider           | `auth-server-url`                                           | Notes                                                                                                                                                                      |
|--------------------|-------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Keycloak           | `https://<host>/realms/<realm>`                             | Roles: `QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH=realm_access/roles` and `QUARKUS_OIDC_ROLES_SOURCE=accesstoken` (realm roles are not in the ID token by default)                |
| AWS Cognito        | `https://cognito-idp.<region>.amazonaws.com/<user-pool-id>` | Roles: `QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH=cognito:groups`                                                                                                                 |
| Microsoft Entra ID | `https://login.microsoftonline.com/<tenant-id>/v2.0`        | Roles: `roles` (app roles) or `groups`                                                                                                                                     |
| Okta               | `https://<org>.okta.com/oauth2/default`                     | Roles: `groups` (add a groups claim to the authorization server)                                                                                                           |
| Google             | `https://accounts.google.com`                               | No roles and no logout endpoint: map roles with emails or `"*"` and restrict with `AUTH_ALLOWED_EMAIL_DOMAINS`, set `QUARKUS_OIDC_AUTHENTICATION_USER_INFO_REQUIRED=true`. |

Register these URLs with your provider:
- redirect URI: `https://<wombat-host>/auth/callback`
- post-logout redirect URI: `https://<wombat-host>/logout/done`

Pure OAuth2 providers such as GitHub are not OpenID Connect and are not supported.

## Roles and permissions

Every user needs a Wombat role, mapped from the roles or groups their provider puts in the role claim (`QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH`, see the table above). Users with no mapped role get `403`.

```sh
AUTH_ROLES_ADMIN=wombat-admins,alice@example.com   # comma-separated provider roles or emails granting "admin"
AUTH_ROLES_USER=wombat-users                       # "*" grants the role to every authenticated user
AUTH_ROLES_API_KEY_MANAGER=wombat-key-managers     # sees and revokes every user's API keys
```

Each `AUTH_ROLES_<ROLE>` (`auth.roles.<role>`) maps one role, and Wombat refuses to start on an unknown role name, so a typo cannot silently leave a role unmapped. Every role extends `user`: an `api-key-manager` also reads the report and manages their own keys, and `admin` holds every permission.

An entry containing `@` matches the user's email (`AUTH_EMAIL_CLAIM`, case-insensitive) instead of a provider role. This is how to give roles to specific people with providers that send no roles, such as Google. The email only counts when the provider flags it verified (`email_verified`), unless `AUTH_REQUIRE_VERIFIED_EMAIL=false`; Entra ID sends no such claim, so use app roles there.

Endpoints check permissions, and each role grants a fixed set of them:

| Permission             | admin | user | api-key-manager |
|------------------------|-------|------|-----------------|
| `ui.dashboard.read`    | ✓    | ✓   | ✓              |
| `ui.apikeys.manage`    | ✓    | ✓   | ✓              |
| `ui.users.read`        | ✓    |      |                 |
| `api.impact.read`      | ✓    | ✓   | ✓              |
| `api.simulation.read`  | ✓    | ✓   | ✓              |
| `api.environment.read` | ✓    | ✓   | ✓              |
| `api.apikey.manage`    | ✓    | ✓   | ✓              |
| `api.apikey.admin`     | ✓    |      | ✓              |
| `api.user.manage`      | ✓    |      |                 |
| `api.user.read`        | ✓    |      |                 |

`AUTH_ROLES_ALLOWED` is gone: replace `AUTH_ROLES_ALLOWED=wombat-users` with `AUTH_ROLES_USER=wombat-users`, or `AUTH_ROLES_USER=*` if every authenticated user had access.

When authentication is disabled (no `oidc` profile), every caller holds every permission.

Wombat records every signed-in user from their requests (at most every 5 minutes, and whenever their roles change). Admins see them on the **Users** page (`/users`, in the **Settings** menu), and can grant them roles there, for example to promote someone to admin. A user holds the roles mapped above plus the ones granted in Wombat, from their next request. Roles from the provider or `AUTH_ROLES_*` show a lock and can only be removed at their source; granted ones can be removed from the page, except an admin's own admin role. The page lists users from `GET /api/users` (`api.user.read`), and changes roles through `PUT` and `DELETE /api/users/{user-id}/roles/{role}` (`api.user.manage`).

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

`/q/health`, `/q/metrics`, `/logout`, the `/signed-out` page, `/api/app` (which reports an anonymous user when there is no session) and the frontend bundle (`/assets/*`, `/icons/*`, `/favicon.png`) stay public. Other paths are protected by the `quarkus.http.auth.permission` entries in [`application-oidc.yaml`](../src/main/resources/application-oidc.yaml).

The UI is a single-page app, but its page paths are still checked on the server before it is served (`wombat-ui-page` policy, bound in [`application.yaml`](../src/main/resources/application.yaml)), after the rules above:

| Page             | Permission          |
|------------------|---------------------|
| `/`, `/impact/*` | `ui.dashboard.read` |
| `/users`         | `ui.users.read`     |
| `/api-keys`      | `ui.apikeys.manage` |

The app also hides the links and pages the user may not open, from the permissions returned by `GET /api/app`. Requests sent by the app carry `X-Requested-With: JavaScript`, so an expired session answers `499` instead of a redirect to the provider, and the app reloads the page to sign in again.

## API keys

Every user can create API keys for their scripts and CI, from the **API keys** page (`/api-keys`, in the **Settings** menu) or the API. A key belongs to the user who created it, is sent in the `X-API-Key` header, and carries a subset of its owner's own `api.*` read permissions (never `ui.*` nor key or user management): asking for one the creator does not hold is refused with `403`.

```sh
curl -X POST https://<wombat-host>/api/keys -H 'Content-Type: application/json' --cookie <session> \
  -d '{"name": "ci", "permissions": ["api.impact.read"], "expiresAt": "2027-01-30T00:00:00Z"}'
curl https://<wombat-host>/api/impact/environments -H 'X-API-Key: <key>'
```

The key is returned once: Wombat only stores its SHA-256 hash, so a lost key cannot be recovered, only revoked and replaced. `GET /api/keys` lists the caller's keys and `DELETE /api/keys/{id}` revokes one of them. Admins and API key managers (`api.apikey.admin`) also list every user's keys with `GET /api/keys?all=true`, the **All users** switch on the page, and can revoke any of them.

Keys are opaque random strings starting with `wmb_` (256 random bits), not JWTs: there is no signing key to configure, and keys survive restarts. The prefix lets secret scanners spot leaked keys. A key sent as `Authorization: Bearer` is rejected: that header is left to the identity provider.

```sh
AUTH_API_KEYS_MAX_LIFETIME=365d            # longest expiry a key can be given
AUTH_API_KEYS_ALLOW_NEVER_EXPIRING=false   # true lets keys be created without "expiresAt"
```

A key works until it expires or is revoked, and never grants more than its owner currently holds: on each call, its permissions are narrowed to those of the owner's roles, so a key stops working once its owner loses them. Keys work the same with every provider, because Wombat never calls it: the owner's roles are the ones mapped at their last request (recorded at most every 5 minutes) plus those granted in Wombat. Removing a granted role applies to the owner's keys at once, but a role removed at the provider only applies once the owner signs in again, and someone who left never does: revoke their keys from the **All users** view, and prefer keys that expire.

## Logout

`/logout` clears the Wombat session. If the provider advertises an `end_session_endpoint` (Keycloak, Entra ID, Okta, Auth0, ...), Wombat also ends the provider session and the provider sends the user back to `/logout/done`. Providers without that endpoint (Google) only get the Wombat session cleared. Both end on the `/signed-out` page, which says whether the provider session was ended too.
