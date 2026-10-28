# Metric compaction

Sourced metrics are written as raw samples every 5 minutes, then periodically folded ("compacted") into fixed windows.
The dashboard and the HTTP API only read compacted rows, so the compaction window is the delay before fresh activity
becomes visible.

| Property             | Default       | Description                                                                                      |
|----------------------|---------------|--------------------------------------------------------------------------------------------------|
| `compaction.cron`    | `0 1 * * * ?` | When compaction runs. Only closed windows are folded, so running it more often than they close is wasted. |
| `compaction.window`  | `1h`          | Width of the compacted windows. Must be a multiple of the 5-minute sampling step and divide an hour (`5m`, `10m`, `15m`, `20m`, `30m`, `1h`); the app refuses to start otherwise. |
| `compaction.delay`   | `1m`          | How long after its end a window is left alone, so the last raw samples land in it before it is folded. |

> [!IMPORTANT]
> Keeping the default settings in production is highly recommended. A shorter window is meant for development, where
> seeing activity within minutes matters more than storage and query cost: it multiplies the rows kept and scanned for
> every range, and makes figures for ranges that start or end mid-hour differ from other deployments.

[`application-dev.yaml.dist`](../src/main/resources/application-dev.yaml.dist) uses a 5-minute window, so changes show
up within minutes while developing.

Effects of a window shorter than an hour:
* **Freshness**: activity is visible about one window plus the delay after it happens, instead of up to an hour later;
  the hour in progress is now partially shown.
* **Same results over whole hours**: every read weights rows by the span they cover, so totals, token and request
  counts, average CPU and container shares do not depend on the window.
* **Sharper range boundaries**: a range is matched against the start of each compacted window, so with hourly windows a
  range starting or ending mid-hour drops or includes a whole hour; a shorter window narrows that error to its width.
  Figures for such ranges can therefore differ slightly from a deployment using the default.
* **Timeline**: the hourly and daily steps are unchanged, but the last hourly point covers an unfinished hour and
  usually appears as a dip until it closes.
* **Storage**: up to 12× more compacted rows with a 5-minute window, and proportionally more rows to scan over long
  ranges.

Changing the window on an existing database is safe in either direction: rows of different widths are weighted
correctly, windows already fully compacted are left as they are, and partially compacted ones are folded again.
