import { describe, expect, it } from 'vitest'
import { computeTimeRange, formatRangeLength, minusMonths } from '@/pages/impact/model/timeRange'

const YEAR_MS = 31_556_952_000
const iso = (date: Date) => date.toISOString()

describe('computeTimeRange (UIController)', () => {
  it('defaults to three months ending at the next midnight', () => {
    const range = computeTimeRange(null, null, YEAR_MS, new Date('2026-06-10T08:15:00Z'))
    expect(iso(range.end)).toBe('2026-06-11T00:00:00.000Z')
    expect(iso(range.start)).toBe('2026-03-11T00:00:00.000Z')
  })

  it('snaps intraday bounds to midnight UTC', () => {
    const range = computeTimeRange('2026-06-04T13:30', '2026-06-10T08:15', YEAR_MS)
    expect(iso(range.start)).toBe('2026-06-04T00:00:00.000Z')
    expect(iso(range.end)).toBe('2026-06-11T00:00:00.000Z')
  })

  it('leaves midnight bounds untouched', () => {
    const range = computeTimeRange('2026-06-04T00:00', '2026-06-11T00:00', YEAR_MS)
    expect(iso(range.start)).toBe('2026-06-04T00:00:00.000Z')
    expect(iso(range.end)).toBe('2026-06-11T00:00:00.000Z')
  })

  it('clamps the range to the max span', () => {
    const range = computeTimeRange('1970-01-01T00:00', '2026-01-01T00:00', 10 * 86_400_000)
    expect(iso(range.end)).toBe('2026-01-01T00:00:00.000Z')
    expect(iso(range.start)).toBe('2025-12-22T00:00:00.000Z')
  })

  it('collapses an inverted range onto its end', () => {
    const range = computeTimeRange('2026-07-01T00:00', '2026-06-01T00:00', YEAR_MS)
    expect(range.start.getTime()).toBe(range.end.getTime())
  })

  it('subtracts months like ZonedDateTime, clamping the day', () => {
    expect(iso(minusMonths(new Date('2026-05-31T00:00:00Z'), 3))).toBe('2026-02-28T00:00:00.000Z')
  })
})

describe('formatRangeLength', () => {
  const length = (start: string, end: string) => formatRangeLength({ start: new Date(start), end: new Date(end) })

  it('counts the default range as three months', () => {
    expect(formatRangeLength(computeTimeRange(null, null, YEAR_MS, new Date('2026-06-10T08:15:00Z')))).toBe('3 months')
  })

  it('counts months back from the end, clamping the day', () => {
    expect(length('2026-02-28T00:00:00Z', '2026-05-31T00:00:00Z')).toBe('3 months')
  })

  it('combines years, months and days', () => {
    expect(length('2025-03-01T00:00:00Z', '2026-05-06T00:00:00Z')).toBe('1 year 2 months 5 days')
    expect(length('2026-06-04T00:00:00Z', '2026-06-11T00:00:00Z')).toBe('7 days')
    expect(length('2026-06-10T00:00:00Z', '2026-06-11T00:00:00Z')).toBe('1 day')
  })

  it('counts the max span as one year', () => {
    expect(formatRangeLength(computeTimeRange('1970-01-01T00:00', '2026-11-25T00:00', YEAR_MS))).toBe('1 year')
  })

  it('reports an empty range', () => {
    expect(length('2026-06-01T00:00:00Z', '2026-06-01T00:00:00Z')).toBe('0 days')
  })
})
