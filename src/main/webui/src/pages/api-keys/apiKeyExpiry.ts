import type { ApiKeyInfo } from '@/domain/access'

export type ApiKeyStatus = 'active' | 'expired' | 'revoked'

export interface ApiKeyExpiryBounds {
  min: string
  max: string
  defaultValue: string
}

function utcDay(date: Date): string {
  return date.toISOString().slice(0, 10)
}

function shiftUtcDay(day: string, days: number): string {
  const date = new Date(`${day}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return utcDay(date)
}

export function apiKeyExpiryBounds(now: Date, maxLifetimeDays: number, defaultLifetimeDays = 30): ApiKeyExpiryBounds {
  const today = utcDay(now)
  const maxDays = Math.max(1, Math.floor(maxLifetimeDays))
  return {
    min: shiftUtcDay(today, 1),
    max: shiftUtcDay(today, maxDays),
    defaultValue: shiftUtcDay(today, Math.min(defaultLifetimeDays, maxDays)),
  }
}

export function clampApiKeyExpiry(value: string, bounds: ApiKeyExpiryBounds): string {
  if (!value || value < bounds.min) return bounds.min
  if (value > bounds.max) return bounds.max
  return value
}

export function apiKeyStatus(key: ApiKeyInfo, now: Date = new Date()): ApiKeyStatus {
  if (key['revoked-at']) return 'revoked'
  if (key['expires-at'] && new Date(key['expires-at']).getTime() <= now.getTime()) return 'expired'
  return 'active'
}

export function filterApiKeys(keys: ApiKeyInfo[], query: string, activeOnly: boolean, now: Date = new Date()): ApiKeyInfo[] {
  const normalized = query.trim().toLowerCase()
  return keys.filter((key) => {
    if (activeOnly && apiKeyStatus(key, now) !== 'active') return false
    if (!normalized) return true
    return key.name.toLowerCase().includes(normalized) || (key['created-by'] ?? '').toLowerCase().includes(normalized)
  })
}
