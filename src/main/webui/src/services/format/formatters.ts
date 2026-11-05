/* Port of tech.illuin.wombat.ui.TemplateFormatters. */

type Instant = Date | string | number | null | undefined

function toDate(instant: Instant): Date | null {
  if (instant === null || instant === undefined || instant === '') return null
  const date = instant instanceof Date ? instant : new Date(instant)
  return Number.isNaN(date.getTime()) ? null : date
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** `yyyy-MM-ddTHH:mm` in UTC, the format of the `from`/`to` query parameters. */
export function asLocalDateTime(instant: Instant): string {
  const date = toDate(instant)
  if (!date) return ''
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}T${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`
}

/** Instant.toString(): ISO-8601 in UTC, without milliseconds when they are zero. */
export function asIsoUtc(instant: Instant): string {
  const date = toDate(instant)
  if (!date) return ''
  return date.toISOString().replace('.000Z', 'Z')
}

export function asPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return `${(value * 100).toFixed(2)}%`
}

export function asDecimal(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  if (value !== 0 && Math.abs(value) < 0.005) {
    const [mantissa, exponent] = value.toExponential(2).split('e')
    const trimmed = mantissa.replace(/\.?0+$/, '')
    const sign = exponent.startsWith('-') ? '-' : '+'
    return `${trimmed}E${sign}${Number(exponent.replace(/^[+-]/, ''))}`
  }
  return value.toFixed(2).replace(/\.?0+$/, '')
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** `MMM d, yyyy HH:mm 'UTC'`, as the users page displayed dates, or "—". */
export function asUserDate(instant: Instant): string {
  const date = toDate(instant)
  if (!date) return '—'
  return `${MONTHS[date.getUTCMonth()]} ${date.getUTCDate()}, ${date.getUTCFullYear()} ${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())} UTC`
}

/** Displays the inclusive UTC calendar days covered by an exclusive-end time range. */
export function formatUtcDayRange(start: Instant, end: Instant): string {
  const startDate = toDate(start)
  const endDate = toDate(end)
  const formatter = new Intl.DateTimeFormat('en-US', {year: 'numeric', month: 'short', day: 'numeric', timeZone: 'UTC'})
  const format = (date: Date | null) => date ? formatter.format(date) : '—'
  const inclusiveEnd = endDate ? new Date(endDate.getTime() - 1) : null
  return `${format(startDate)} → ${format(inclusiveEnd)} (UTC)`
}
