# Secret Management

Wombat assets sometimes include sources or activity resolvers that need to interact with other systems.
In such cases, said systems will probably require authentication of some kind, which means the wombat assets needs to be able to reference such credentials in a secure fashion.

For this purpose, Wombat uses a `SecretResolver` abstraction for providing credentials at instantiation time.

## Secret Resolver Chain

The `SecretResolver` is a composite that checks the elected providers in priority order. The first provider that returns a value for a key supplies it; a missing key falls through to the next provider. In `DATABASE` write mode the database resolver is first, followed by the configured keystore, the configured directory, and the environment. The database resolver is omitted when database mode is not enabled.

```mermaid
graph LR
    Request --> Database[Database if enabled]
    Database --> Keystore[Keystore if configured]
    Keystore --> Directory[Directory if configured]
    Directory --> Environment[Environment]
```

The arrows indicate trying the next source when a key is missing, not when a resolver fails. In particular, database errors (such as an unavailable database or a secret-integrity error) propagate; they do not silently fall back to another source.

- **Database:** Included only when `wombat.secret.write-mode` is `database`. It has the highest lookup priority.
- **Keystore:** Optional; configure `wombat.secret.keystore.path`. The optional `wombat.secret.keystore.password` is used to load the keystore and as the default key password. `wombat.secret.keystore.type` selects the Java keystore type and defaults to `PKCS12`. An absent or blank path disables this resolver. Entry support depends on the selected keystore type and provider; not every type supports secret-key entries.
- **Directory:** Optional; configure `wombat.secret.directory.path` to a directory containing one UTF-8 file per secret, named with the requested key. An absent or blank path disables this resolver. A missing file or blank file content is treated as a missing key.
- **Environment:** Resolves from the process environment and requires no provider-specific `wombat.secret.*` configuration.

## Secret administration

The secret-management API uses the selected `SecretWriter`. These operations affect that writable backend only; they do not update every configured resolver or make read-only sources such as the environment, directory, or keystore manageable through the API.

| Method and path | Operation |
|---|---|
| `GET /api/secrets` | List writer metadata for each key: the key and its creation and update timestamps. Values are not returned. |
| `PUT /api/secrets` | Create or replace a value with JSON `{"key":"...","value":"..."}`. Keys and values must not be blank. |
| `DELETE /api/secrets?key=...` | Delete the key from the selected writer. A blank key is rejected; a key that is not found returns `404`. |

Use TLS for clients and protect any connection between a TLS-terminating proxy and Wombat. With authentication enabled, only an authenticated `ADMIN` has `api.secret.manage`; API keys cannot be granted that permission. In `DISABLED` mode the endpoints are disabled. When authentication is disabled and a writable backend is selected, anonymous callers receive the same permissions as other callers and can manage secrets. This exposes secret management to anyone who can reach the service, so keep authentication enabled on reachable deployments. OpenAPI shows the operations when the write mode is `database`, regardless of authentication state, and omits them when writes are disabled.

No management endpoint returns plaintext values. Send write bodies using a trusted client without exposing them in shell history, process arguments, proxy request-body logs, or other telemetry. The application audit records actor, operation, and outcome without the value or cryptographic material.

Quarkus does not support runtime changes to `@EndpointDisabled` in native builds. Use the JVM distribution when selecting write mode at startup; do not rely on native images to switch secret-controller availability at runtime.

## Environment-backed `SecretResolver`

The process environment is the final resolver in the chain and requires no provider-specific configuration. It is consulted when no earlier enabled provider supplies a value.

## Directory-backed `SecretResolver`

Set `wombat.secret.directory.path` to the directory containing secret files. Each secret is stored as UTF-8 text in a regular file named for its key. If the path is absent or blank, the directory resolver is disabled. A missing file or blank value allows lookup to continue to the next resolver.

## Keystore-backed `SecretResolver`

Set `wombat.secret.keystore.path` to the keystore file. The optional `wombat.secret.keystore.password` supplies the keystore password and the default key password. Set `wombat.secret.keystore.type` to select the keystore type; it defaults to `PKCS12`. An absent or blank path disables this resolver. The resolver can read only entries supported by the selected keystore type and provider, so do not assume every type supports secret-key entries. A missing alias/value allows lookup to continue to the next resolver.

## Database-backed `SecretResolver`

`database` is the currently supported write mode. The default `wombat.secret.write-mode` is `DISABLED`, in which secret-management writes are unavailable and configured environment, directory, and keystore resolvers remain read-only. When `database` is selected, the database resolver is enabled and is consulted before the keystore, directory, and environment resolvers.

Required secrets are checked early in `DISABLED` mode, and after database restore and migration but before environment reconciliation in `database` mode. An initially empty database cannot supply credentials needed to start its own configured assets: first provide them through a lower-priority source, or start with no assets requiring them, then populate the database before enabling those assets. Database resolution still works when authentication is disabled.

Wombat stores secrets in the database using envelope encryption. The database contains ciphertext and wrapped data-encryption keys, not plaintext values or key-encryption keys (KEKs). Each write uses a fresh AES-256-GCM data key and independent nonces. Deleting a key removes its live database row, but ciphertext for that row can remain in database backups until those backups expire; backups do not contain the KEKs needed to decrypt the stored data.

Select `database` and supply each KEK as Base64-encoded 32-byte key material from an external secret manager or environment variable. The active identifier must name one of the configured keys. For example, with the values supplied outside the configuration file:

```yaml
wombat:
  secret:
    write-mode: database
    database:
      active-kek-id: "new"
      kek:
        new: "${WOMBAT_SECRET_KEK_NEW}"
        old: "${WOMBAT_SECRET_KEK_OLD}"
```

Obtain KEKs from your organization's approved secret-management service, or generate cryptographically random key material on a trusted machine and import it directly into that service. For example, `openssl rand -base64 32` generates standard Base64 for 32 random bytes (256 bits), which is the format Wombat expects. Wombat needs the actual key material, not a KMS key ID or alias; a non-exportable KMS key cannot be used directly. Keep each key stable under its configured ID across all instances and retain it securely for as long as live data or backups depend on it. Inject it at runtime through your secret manager or environment rather than putting it in configuration files.

> ⚠️ Do not commit KEK material or put it in the database, database backups, application logs, or request bodies. Protect the external key store and retain the key identifiers and their associated material consistently across instances. Only the active key is necessary for a new store; previous keys must also be configured while rows or retained backups still depend on them.

### KEK rotation and backups

1. Generate a new 256-bit KEK externally. Distribute both old and new KEKs, under consistent IDs, to **every** instance before changing the active ID.
2. Roll out the new active ID to every instance while retaining the old KEKs. Wait for the rollout to finish before rewrapping, so an old instance cannot keep writing under the previous active key.
3. Call the database-specific `POST /api/secrets/database/rewrap` endpoint repeatedly. Each request processes at most ten batches of 100 rows in separate transactions; committed batches survive interruption. The response reports scanned, rewrapped, failure, conflict, and remaining counts. Retry until `remaining` is zero, and investigate failures or conflicts; missing old KEKs and corrupted wrapped keys remain unmodified. Persistent failures are revisited on each request and can occupy the bounded scan, so resolve them before expecting progress through later rows. Keep old KEKs available until all live rows are rewrapped.
4. Retain every old KEK outside the database and backups for as long as any retained backup might need it. Restoring an older backup requires its KEKs even after all live rows have been rewrapped. Retire a key only after dependent backups have expired and no instance or live row needs it.

Rewrapping changes only the wrapped data key, its wrapping nonce, KEK identifier, and concurrency revision; it does not decrypt or rewrite the secret payload. Failed or interrupted operations can be retried safely.