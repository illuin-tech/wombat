# Environments and provisioning

After launching Wombat, Environments and their assets are created from the **Environments** page, or through `api/environments` and `api/assets`.
Monitoring starts as soon as an asset is saved.

Provisioning is an optional alternative: environments declared in a YAML file, kept in sync at every startup.
It suits deployments managed as code (Helm, Pulumi, Docker Compose), where the monitored perimeter is reviewed and versioned like the rest.
Both approaches can be used side by side.

## The environments file

Wombat reads the file at `wombat.monitor.environments-file` (`WOMBAT_MONITOR_ENVIRONMENTS_FILE`), which defaults to `monitored/monitored-environments.yaml` relative to the working directory,
i.e. `/deployments/monitored/monitored-environments.yaml` in the Docker image.
When there is no file there, Wombat provisions nothing and logs it at startup:

```
INFO  [tech.illuin.wombat.engine.monitor.MonitorConfig] No monitored environments file at 'monitored/monitored-environments.yaml', no environment is provisioned
```

The file maps environment ids to their assets:

```yaml
environments:
  demo:
    id: demo
    assets:
      - type: tech.illuin.wombat-module.llm-static
        id: demo-llm
        environment-id: demo
        profile:
          zone-id: Europe/Paris
          models:
            - provider: mistralai
              model: mistral-large-latest
              location: FRA
              request-per-year: 1000000
              output-token-per-request: 400
              scenarios:
                - type: constant
```

Each asset type is documented with its module, in the[`wombat-core` documentation](https://github.com/illuin-tech/wombat-core#core-modules).
The [`monitored-environments.yaml.dist`](../compose/demo-standalone/monitored-environments.yaml.dist) template covers the static types, and comments a live Kubernetes cluster.

## What provisioning means

* The file is the source of truth for what it declares. At every startup, the environments and assets it declares are created or updated, and those it no longer declares are deleted, with their history recorded.
  Removing the file deletes them all at the next startup. Their past metrics are kept, and they come back with their history once declared again.
* Provisioned environments and assets are read-only from the pages and the API: they show a **Provisioned** lock, and editing or deleting one is a `409`. Change the file instead.
* The file never touches what was created from the pages or the API. Declaring an id one of them already holds fails the startup.
* Wombat does not start when a provisioned asset needs a secret that no secret source provides. See [secret management](secret-management.md).

## Workers

A [worker](distributed.md#workers) serves no page nor API: its environments file is the only way to tell it what to sample.
It declares the file's environments to its aggregator, which provisions them the same way, as read-only.
