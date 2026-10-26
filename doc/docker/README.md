# Wombat Carbon Tracker

Wombat is a multi-criteria environmental impact tracker for digital and AI services.

Its goal is to streamline the use of astounding work being done by others on environmental footprint calculators, methodology and referential data (namely works from [Boavizta](https://boavizta.org/en), [SustainableAIGroup](https://reports.sustainableaigroup.com/CLEER-Tech-Report/), [Ecologits](https://ecologits.ai)..) and use it where it has the biggest impact:
* at the **_software design stage_**, so as to inform key solution sizing decisions
* after launch, at the **_operations stage_**, so as to inform decision makers on the evolution of their solution's impact

To do that, we focused on a few key ideas:
* a tool that is adaptable regarding **_data sources_**, to make it easier to aggregate quality data, but also offer fallback strategies when that data isn't readily available
* a tool that is **_easy to deploy_**, so as to be able to justify setting it up alongside a lone AI stack, just like it can aggregate data from dozens of different projects in a more centralized fashion
* a tool that **_doesn't reinvent the wheel_**, there are many people working on other challenging parts of the estimation process, so we built a modular system with integrations for calculation engines like Boavizta, Ecologits (and soon CLEER), we also expect to encounter situations where methodology has to be tweaked in one way or another: we built wombat with that in mind
* a tool that tries to **_contextualize ecological impact_** with financial and business metrics: these combined metrics can help paint a complete picture of how much your AI stack costs; _most often_ a large LLM that is costly to use also has a large environmental footprint

In short, Wombat is:
* a Java app (this repository) deployable as [a docker container](https://hub.docker.com/r/illuin/wombat) directly, or on K8S through a [pulumi resource or helm chart](https://github.com/illuin-tech/wombat-k8s)
    * the app exposes a [dashboard](#dashboard) and [HTTP endpoints](#http-api)
* a Java SDK, [wombat-core](https://github.com/illuin-tech/wombat-core), for creating custom modules or directly integrating its core features into other systems

More complete documentation can be found [over at GitHub](https://github.com/illuin-tech/wombat).

## How to run

Here is a sample docker-compose local setup for:

* Wombat app
* local boavizta & ecologits APIs
* temporary sqlite + periodic S3 backup (this is a demo ; on a local setup you would rather swap that out for an sqlite db file written to a docker volume and do away with the backup)


```yaml
services:
  wombat:
    container_name: wombat
    image: illuin/wombat:latest
    ports:
      - "8080:8080"
    volumes:
      - ./application.yaml:/deployments/config/application.yaml:ro
      - ./monitored-environments.yaml:/deployments/monitored/monitored-environments.yaml:ro
      - ~/.kube:/deployments/monitored/k8s:ro # you can load them from another local directory
      - ./data:/var/data:rw

  boavizta:
    container_name: boavizta
    image: ghcr.io/boavizta/boaviztapi:2.4.1

  ecologits:
    container_name: ecologits
    image: ghcr.io/mlco2/ecologits-api:0.0.2
```

With the following `application.yaml` file:

```yaml
quarkus:
  datasource:
    jdbc:
      url: "jdbc:sqlite:/var/data/local.db"

connector:
  boavizta:
    uri: "http://boavizta:5000/v1/"
  ecologits:
    uri: "http://ecologits:80/"
```

And the following `monitored-environments.yaml` file:

```yaml
environments:
  demo:
    id: demo
    assets:
      # This type works by querying the kubernetes metrics API and gathering CPU/RAM load factors for each deployed container.
      - type: tech.illuin.wombat-module.kubernetes-api
        id: prod-cluster
        environment-id: demo
        name: Demo Cluster
        namespace: my-project-namespace # this should match the namespace you deployed your applications to ; you can create several assets of the same time if multiple namespaces are monitored
        config-path: /deployments/monitored/k8s/my-kube.config # this should point at your kubeconfig file ; note that the path coincides with the mounting point in the compose.yaml file
        profile: # this should be tweaked to match the node your workloads is running on
          provider: aws
          instance-type: c5.xlarge
          location: FRA
          lifespan: 43800
      # This type works with a request yearly estimate and will prorate it dynamically.
      # This is the bare minimum available in some situations.
      - type: tech.illuin.wombat-module.llm-static
        id: demo-llm
        environment-id: demo
        name: LLM Services
        profile:
          models: # this should be tweaked to match your model & model usage mix
            - provider: mistralai
              model: mistral-large-latest
              location: FRA
              request-profile:
                output-token-count: 500
                request-per-year: 500000
            - provider: openai
              model: mistral-large-latest
              location: SWE
              request-profile:
                output-token-count: 150
                request-per-year: 8000000
```

And a valid kubeconfig in `~/.kube`, in the configuration above we've named it `my-kube.config`.

### Custom Extensions

Wombat allows user to provide custom modules built with `wombat-core` (see [repository](https://github.com/illuin-tech/wombat-core)) to extend its functionality.

Custom modules (e.g. `wombat-simulator`) are loaded **_at startup_** from every JAR found in `module.extension.path`
(defaults to `extensions`, relative to the working directory, i.e. `app/extensions` when running from `app/`). Override it
with `-Dmodule.extension.path=...` or `MODULE_EXTENSION_PATH=...`.

When starting up the app with a custom extension, you should see logs like these:

```
2026-10-01 16:42:56,837 INFO  [tech.illuin.wombat.module.extension.DynamicExtensionLoader] (Quarkus Main Thread) Loading extensions from /path/to/extensions (1 jar(s))
2026-10-01 16:42:56,839 INFO  [tech.illuin.wombat.module.extension.DynamicExtensionLoader] (Quarkus Main Thread) Discovered extension module tech.illuin.wombat.module.simulator.k8s.KubernetesSimulatorModule for asset-type tech.illuin.wombat-simulator.kubernetes
2026-10-01 16:42:56,839 INFO  [tech.illuin.wombat.module.extension.DynamicExtensionLoader] (Quarkus Main Thread) Discovered extension module tech.illuin.wombat.module.simulator.llm.LLMSimulatorModule for asset-type tech.illuin.wombat-simulator.llm
2026-10-01 16:42:56,839 INFO  [tech.illuin.wombat.module.WombatModuleConfig] (Quarkus Main Thread) Wiring 6 wombat module(s): [KubernetesSimulatedModule, LLMSimulatedModule, LLMStaticModule, KubernetesAPIModule, KubernetesSimulatorModule, LLMSimulatorModule]
```
