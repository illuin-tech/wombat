# Secret Management

Wombat assets sometimes include sources or activity resolvers that need to interact with other systems.
In such cases, said systems will probably require authentication of some kind, which means the wombat assets needs to be able to reference such credentials in a secure fashion.

For this purpose, Wombat uses a `SecretResolver` abstraction for providing credentials at instantiation time.

## Secret Resolver Chain

The `SecretResolver` is a composite structure that checks the elected providers in priority order. The first provider that returns a value for a key supplies it; a missing key falls through to the next provider. In `database` write-mode the database resolver is first, followed by the configured keystore, the configured directory, and the environment.

The database resolver is omitted when database mode is not enabled.

```mermaid
graph LR
    REQUEST[Request]

    subgraph WRITEABLE[Write-able SecretResolver]
        direction LR
        DATABASE[Database - optional]
        VAULT[Vault - optional]
    end
    
    subgraph READ_ONLY[Read-only SecretResolver]
        direction LR
        
        KEYSTORE[Keystore - optional]
        DIRECTORY[Directory - optional]
        ENVIRONMENT[Environment]
        
        KEYSTORE --> DIRECTORY
        DIRECTORY --> ENVIRONMENT
    end
    
    REQUEST -.-> DATABASE
    REQUEST -.-> VAULT
    DATABASE -.-> KEYSTORE
    VAULT -.-> KEYSTORE
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

Use TLS for clients and protect any connection between a TLS-terminating proxy and Wombat. With authentication enabled, only an authenticated `ADMIN` has `api.secret.manage`; API keys cannot be granted that permission. In `disabled` mode the endpoints are disabled. When authentication is disabled and a writable backend is selected, anonymous callers receive the same permissions as other callers and can manage secrets. This exposes secret management to anyone who can reach the service, so keep authentication enabled on reachable deployments. OpenAPI shows the operations when the write mode is `database`, regardless of authentication state, and omits them when writes are disabled.

No management endpoint returns plaintext values. Send write bodies using a trusted client without exposing them in shell history, process arguments, proxy request-body logs, or other telemetry. The application audit records actor, operation, and outcome without the value or cryptographic material.

Quarkus does not support runtime changes to `@EndpointDisabled` in native builds. Use the JVM distribution when selecting write mode at startup; do not rely on native images to switch secret-controller availability at runtime.

## Environment-backed `SecretResolver`

The process environment is enabled automatically and requires no Wombat resolver property. The environment variable name corresponds to the requested secret key.

The environment resolver is consulted when no earlier enabled provider supplies the requested secret.

## Directory-backed `SecretResolver`

Set `wombat.secret.directory.path` to the directory containing secret files. The property is optional (default: not configured); an absent or blank path disables this resolver.

```yaml
wombat:
  secret:
    directory:
      path: "/run/secrets" # optional (default: not configured; resolver disabled) - directory containing one file per secret.
```

Each secret is stored as UTF-8 text in a regular file named for its key. A missing file or blank value allows lookup to continue to the next resolver.

## Keystore-backed `SecretResolver`

Set `wombat.secret.keystore.path` to the keystore file to enable this resolver. The path is optional (default: not configured); an absent or blank path disables the resolver. The optional password has no default and is used to load the keystore and as the default key password. The optional keystore type defaults to `PKCS12`.

```yaml
wombat:
  secret:
    keystore:
      path: "/run/secrets/keystore.p12" # optional (default: not configured; resolver disabled) - keystore file.
      password: "${WOMBAT_SECRET_KEYSTORE_PASSWORD}" # optional (default: no password) - keystore and default key password.
      type: "PKCS12" # optional (default: PKCS12) - Java keystore type.
```

Supply the password through external configuration rather than committing it. The resolver can read only entries supported by the selected keystore type and provider, so do not assume every type supports secret-key entries. A missing alias/value allows lookup to continue to the next resolver.

## Database-backed `SecretResolver`

The `database` resolver is a read/write resolver that can be enabled with the `wombat.secret.write-mode: database` option (default value is `disabled`). When `database` is selected, the database resolver is enabled and is consulted before the keystore, directory, and environment resolvers.

> ⚠️ We want to stress that the `database` mode is intended for "simple" setups where no other secret management is available. For a typical production setup we highly recommend using a dedicated key-store like hashicorp/vault: it will be simpler to manage in the long run and you can trust their robustness with regards to security. That being said, if this is not something you can count on, the `database` module is designed to be resilient enough for production use.

> ⚠️ If you do use a third-party key-store but it does not have a Wombat integration, don't hesitate to reach out to us so we can figure out a way to add support for it.

Required secrets are checked early in `disabled` mode, and after database restore and migration but before environment reconciliation in `database` mode. An initially empty database cannot supply credentials needed to start its own configured assets: first provide them through a lower-priority source, or start with no assets requiring them, then populate the database before enabling those assets. Database resolution still works when authentication is disabled.

Wombat stores secrets in the database using envelope encryption. The database contains ciphertext and wrapped data-encryption keys, not plaintext values or key-encryption keys (KEKs). Each write uses a fresh AES-256-GCM data key and independent nonces. Deleting a key removes its live database row, but ciphertext for that row can remain in database backups until those backups expire; backups do not contain the KEKs needed to decrypt the stored data.

When database mode is enabled, `wombat.secret.database.active-kek-id` (default: not configured) must name a configured key in `wombat.secret.database.kek` (default: empty). Supply each KEK as Base64-encoded 32-byte key material from an external secret manager or environment variable. The example uses placeholders so that key material is supplied outside the configuration file:

```yaml
wombat:
  secret:
    write-mode: database # optional (default: DISABLED) - set to `database` to enable database-backed writes and resolution.
    database:
      active-kek-id: "new" # required in `database` mode - must match one of the configured KEK IDs.
      kek:
        new: "${WOMBAT_SECRET_KEK_NEW}" # required active key - Base64 for 32 bytes, provided externally.
        old: "${WOMBAT_SECRET_KEK_OLD}" # required only while stored rows or retained backups depend on this key.
```

Obtain KEKs from your organization's approved secret-management service, or generate cryptographically random key material on a trusted machine and import it directly into that service. For example, `openssl rand -base64 32` generates standard Base64 for 32 random bytes (256 bits), which is the format Wombat expects. Wombat needs the actual key material, not a KMS key ID or alias; a non-exportable KMS key cannot be used directly. Keep each key stable under its configured ID across all instances and retain it securely for as long as live data or backups depend on it. Inject it at runtime through your secret manager or environment rather than putting it in configuration files.

> ⚠️ Do not commit KEK material or put it in the database, database backups, application logs, or request bodies. Protect the external key store and retain the key identifiers and their associated material consistently across instances. Only the active key is necessary for a new store; previous keys must also be configured while rows or retained backups still depend on them.

### KEK rotation and backups

1. Generate a new 256-bit KEK externally. Distribute both old and new KEKs, under consistent IDs, to **every** instance before changing the active ID.
2. Roll out the new active ID to every instance while retaining the old KEKs. Wait for the rollout to finish before rewrapping, so an old instance cannot keep writing under the previous active key.
3. Call the database-specific `POST /api/secrets/database/rewrap` endpoint repeatedly. Each request processes at most ten batches of 100 rows in separate transactions; committed batches survive interruption. The response reports scanned, rewrapped, failure, conflict, and remaining counts. Retry until `remaining` is zero, and investigate failures or conflicts; missing old KEKs and corrupted wrapped keys remain unmodified. Persistent failures are revisited on each request and can occupy the bounded scan, so resolve them before expecting progress through later rows. Keep old KEKs available until all live rows are rewrapped.
4. Retain every old KEK outside the database and backups for as long as any retained backup might need it. Restoring an older backup requires its KEKs even after all live rows have been rewrapped. Retire a key only after dependent backups have expired and no instance or live row needs it.

Rewrapping changes only the wrapped data key, its wrapping nonce, KEK identifier, and concurrency revision; it does not decrypt or rewrite the secret payload. Failed or interrupted operations can be retried safely.