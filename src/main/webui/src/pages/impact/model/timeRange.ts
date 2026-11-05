/* Port of UIController.computeTimeRange: the dashboard defaults, computed in UTC. */

export const DEFAULT_RANGE_MONTHS = 3
const DAY_MS = 86_400_000
const QUERY_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/

export interface TimeRange {
  start: Date
  end: Date
}

/** Parses `yyyy-MM-ddTHH:mm[:ss]` as UTC, or returns null. */
export function parseQueryDate(value: string | null | undefined): Date | null {
  if (!value) return null
  const match = QUERY_PATTERN.exec(value.trim())
  if (!match) return null
  const [, y, mo, d, h, mi, s] = match
  const date = new Date(Date.UTC(Number(y), Number(mo) - 1, Number(d), Number(h), Number(mi), Number(s ?? 0)))
  return Number.isNaN(date.getTime()) ? null : date
}

export function truncateToDay(date: Date): Date {
  return new Date(Math.floor(date.getTime() / DAY_MS) * DAY_MS)
}

export function ceilToDay(date: Date): Date {
  const day = truncateToDay(date)
  return day.getTime() === date.getTime() ? date : new Date(day.getTime() + DAY_MS)
}

/** ZonedDateTime.minusMonths: same day of month, clamped to the target month's length. */
export function minusMonths(date: Date, months: number): Date {
  const year = date.getUTCFullYear()
  const month = date.getUTCMonth() - months
  const lastDay = new Date(Date.UTC(year, month + 1, 0)).getUTCDate()
  return new Date(Date.UTC(
    year, month, Math.min(date.getUTCDate(), lastDay),
    date.getUTCHours(), date.getUTCMinutes(), date.getUTCSeconds(), date.getUTCMilliseconds(),
  ))
}

export function computeTimeRange(from: string | null | undefined, to: string | null | undefined, maxSpanMs: number, now: Date = new Date()): TimeRange {
  const end = ceilToDay(parseQueryDate(to) ?? now)
  const parsedFrom = parseQueryDate(from)
  let start = parsedFrom ? truncateToDay(parsedFrom) : minusMonths(end, DEFAULT_RANGE_MONTHS)
  if (start.getTime() > end.getTime())
    start = end
  if (end.getTime() - start.getTime() > maxSpanMs)
    start = ceilToDay(new Date(end.getTime() - maxSpanMs))
  return { start, end }
}
