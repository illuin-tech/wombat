import { describe, expect, it } from 'vitest'
import { asDecimal, asIsoUtc, asLocalDateTime, asPercent, asUserDate, formatUtcDayRange } from '@/services/format/formatters'

describe('formatters (TemplateFormattersTest)', () => {
  it('formats local date times in UTC', () => {
    expect(asLocalDateTime('2026-06-04T13:30:00Z')).toBe('2026-06-04T13:30')
    expect(asLocalDateTime(null)).toBe('')
  })

  it('formats ISO instants without zero milliseconds', () => {
    expect(asIsoUtc('2026-06-04T13:30:00Z')).toBe('2026-06-04T13:30:00Z')
    expect(asIsoUtc(undefined)).toBe('')
  })

  it('formats percentages', () => {
    expect(asPercent(0.425)).toBe('42.50%')
    expect(asPercent(0)).toBe('0.00%')
    expect(asPercent(null)).toBe('—')
  })

  it('formats decimals with trimmed zeros and scientific notation for tiny values', () => {
    expect(asDecimal(3.14)).toBe('3.14')
    expect(asDecimal(5)).toBe('5')
    expect(asDecimal(100)).toBe('100')
    expect(asDecimal(0)).toBe('0')
    expect(asDecimal(0.0001)).toBe('1E-4')
    expect(asDecimal(0.00015)).toBe('1.5E-4')
    expect(asDecimal(null)).toBe('—')
  })

  it('formats user dates like the users page', () => {
    expect(asUserDate('2026-06-04T13:30:00Z')).toBe('Jun 4, 2026 13:30 UTC')
    expect(asUserDate(null)).toBe('—')
  })

  it('formats inclusive UTC day ranges from an exclusive end instant', () => {
    expect(formatUtcDayRange('2026-06-04T00:00:00Z', '2026-06-07T00:00:00Z')).toBe('Jun 4, 2026 → Jun 6, 2026 (UTC)')
    expect(formatUtcDayRange(null, null)).toBe('— → — (UTC)')
  })
})
