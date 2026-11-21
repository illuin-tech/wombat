# Simulations

A **simulation** is a sandbox for estimating what systems would emit and cost: a set of asset definitions, copied from
real assets or written from scratch, then changed freely. They are evaluated on demand, and never monitored: nothing is
sampled for them, and their results are not stored.

Simulations are managed from the **Simulations** page, or through `api/simulation/sandbox`. The stateless
`POST /api/simulation`, which evaluates an ad-hoc payload at once, is unrelated to them.

## Simulations and their assets

A simulation has an id, a name, a description, notes and a [reference window](#evaluation). It is created:
* **from an existing environment**: the assets picked are copied into it, and the environment is kept as its source;
* **from scratch**: it starts empty.

Assets are then added to it, either as copies of an asset of any environment, or written from scratch with any
creatable type whose module is enabled. A simulation asset has its own notes, and its id is unique within its
simulation only: two simulations, or two copies of one asset in a simulation, are evaluated apart.

The pages don't offer some assets:

| Asset                   | Why                                                                                                                         |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| Of an unknown type      | Its module is missing: there is nothing to evaluate it with.                                                                |
| Of a disabled module    | Offered once the module is enabled again. Through the API, such an asset is accepted, and evaluated as `module-disabled`.  |
| Of a `*-simulated` type | Their identity is fixed, and their activity comes with them. The API refuses them too (400): use `llm-static` or `kubernetes-static`, whose scenarios resolve activity over any window. |

A copy takes its source's id, name and description, unless given its own. Its id has any character outside `[a-z0-9_-]` replaced by `-` (a worker's
`worker.chatbot` becomes `worker-chatbot`), and `-2`, `-3`… appended when that id is taken in the simulation.

### Copies and their source

A copy is a snapshot: it does not follow its source. A copy's `source` holds the source's `assetId` and
`environmentId` (as they were at copy time), when it was last `syncedAt`, and its `status`, from comparing their
definitions (identities aside). An asset written from scratch has no `source`.

| `source.status`  | Meaning                                                                                         |
|------------------|-------------------------------------------------------------------------------------------------|
| `in-sync`        | The copy and its source have the same definition.                                               |
| `diverged`       | Either was changed since the copy was created, or last synced. The comparison can't tell which. |
| `source-removed` | The source was deleted. The copy is kept as it is, and can no longer be synced.                 |

A simulation asset belongs to no environment: unlike an environment's asset, it is served without `environmentId`.

**Sync** (`POST …/assets/{assetId}/sync`) replaces the copy's definition with its source's current one, and loses the
changes made to the copy.

A **duplicate** of a simulation copies all its assets, with their links to their sources. Deleting a simulation deletes
its assets, and leaves their sources as they are.

## Evaluation

A simulation is evaluated once, over its **reference window**, then each result is scaled to the period shown:

| Period | Length   | Window ends at                 |
|--------|----------|--------------------------------|
| Day    | 24 hours | the current hour               |
| Week   | 7 days   | midnight UTC                   |
| Month  | 30 days  | midnight UTC                   |
| Year   | 365 days | midnight UTC                   |

The reference window defaults to a month. Scaling is linear: a period's results are the window's times
`period / window`, the `factors` of the response. From a month, a day is 1/30 of it and a year 365/30.

The window shifts the results:
* A copy of a monitored asset reads the activity recorded over the window. A short window follows the latest activity,
  but whatever happened in it (a peak, a quiet weekend, an outage) is scaled as if it lasted the whole period. A longer
  window smooths those out, and follows recent changes more slowly.
* Where the window has no recorded activity, it counts as none, which lowers the results. The **coverage** of an asset
  is the share of the window's hours (for a day) or days with activity. The page notes it for measured types under
  100%: there, a gap means missing data rather than a quiet hour of a scenario.
* A modeled asset computes its activity from its scenarios, so the window only matters for a scenario that varies over
  a longer cycle than the window.

The impact and the cost are computed once per asset and evaluation, over the whole window, whatever its length; the
coverage takes one more activity query. Nothing is cached: each evaluation builds the asset's components
afresh, from its definition as stored, on the core deployed at that moment (see [modules](modules.md)).

### Identities

An evaluation reads a copy's activity under its source's identity, `(source id, source environment, type)`, so that it
finds the source's metrics. Everything else, the impact, the cost and the activity of an asset written from scratch, is
computed under an identity of the simulation asset's own, internal to the app and never served. Each asset is evaluated
with components built for it alone, so the changes made to a copy never reach the dashboard, nor `api/impact`.

> **Caveat:** metrics are looked up by asset id alone. An asset written from scratch, of a measured type with a default
> resolver (`llm-prometheus`, `kubernetes-api`…), would read the metrics of any real asset with the same id. Such an
> asset has no data source of its own, so it is an odd case; a core fix will scope the lookups to the environment.

### Results

Each asset gets a status:

| Status            | Meaning                                                                                   |
|-------------------|-------------------------------------------------------------------------------------------|
| `ok`              | Evaluated: its results are in the totals.                                                 |
| `no-data`         | Its resolvers found no activity in the window.                                            |
| `module-disabled` | The deployed core has no module for its type, e.g. disabled since the asset was created.  |
| `error`           | The evaluation failed, e.g. a provider refused it: `message` tells why.                   |

`POST /api/simulation/sandbox/{id}/evaluation` returns, for the scopes asked (`IMPACT`, `COST`, both by default) and an
optional `referenceWindow` overriding the simulation's:

```
{
  window: { start, end, period },
  factors: { DAY: 0.033, WEEK: 0.233, MONTH: 1, YEAR: 12.17 },
  totals: { DAY: { impact, cost }, … },               // summed over the assets evaluated ok; cost: [{ value, currency }]
  assets: [{
    id, status, message, coverage,
    result: [AssetImpact | AssetCost],                // over the window, straight from the evaluator, told apart by `type`
    scaled: {                                         // the standardized part of `result`, scaled to each period
      DAY: { impact, cost, services: [{ serviceId, assetType, impact, cost }] }, …
    }
  }]
}
```

The server does all the scaling: a client reads the period it shows and never multiplies anything. Only the
standardized figures are scaled, the footprints and the costs, the asset's and each service's; the details of `result`
(requests, CPU load…) cover the window, and leave out the asset's identity, which `id` gives.

A cost is a list with one amount per currency (`{ value, currency }`, an ISO 4217 code), summed per currency and never
converted. It is null when the cost is not evaluated, and empty when nothing was priced: no module prices assets yet, so
the page shows "Not computed".

## Permissions

| Permission              | Grants                                                                    | Held by          |
|-------------------------|---------------------------------------------------------------------------|------------------|
| `api.simulation.read`   | Reading and evaluating simulations                                        | Every role       |
| `api.simulation.write`  | Creating, changing, duplicating and deleting simulations and their assets | `admin`, and API keys granted it |
| `ui.simulations.read`   | The Simulations pages                                                     | Every role       |
| `ui.simulations.manage` | Creating simulations from the pages, and editing them                     | `admin`          |

Copying assets also takes `api.asset.read`, and only the assets visible to the caller can be copied. Writing is
admin-only for the reason asset writes are: an asset is evaluated with the app's secrets, and someone able to point a
copy's endpoint and secret reference anywhere could send a stored secret out. See [access control](access-control.md).

## API

All under `api/simulation/sandbox`:

| Method   | Path                              | Does                                                      |
|----------|-----------------------------------|-----------------------------------------------------------|
| `GET`    | `/`                               | Lists the simulations, with their asset counts            |
| `POST`   | `/`                               | Creates one, copying `assetIds` of `sourceEnvironmentId`  |
| `GET`    | `/{id}`                           | Reads one, with its assets                                |
| `PUT`    | `/{id}`                           | Updates its name, description, notes and reference window |
| `DELETE` | `/{id}`                           | Deletes it, with its assets                               |
| `POST`   | `/{id}/duplicate`                 | Duplicates it under a new id and name                     |
| `POST`   | `/{id}/evaluation`                | Evaluates it                                              |
| `GET`    | `/{id}/assets`                    | Lists its assets, with how each copy stands               |
| `POST`   | `/{id}/assets`                    | Adds a copy of `sourceAssetId`, or an asset from scratch  |
| `GET`    | `/{id}/assets/{assetId}`          | Reads an asset, compared with its source as it is now     |
| `PUT`    | `/{id}/assets/{assetId}`          | Updates an asset's name, description, notes and properties |
| `DELETE` | `/{id}/assets/{assetId}`          | Removes an asset                                          |
| `POST`   | `/{id}/assets/{assetId}/sync`     | Syncs a copy with its source                              |

The schemas are in the OpenAPI document (`api/openapi`).
