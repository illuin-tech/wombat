import { describe, expect, it } from 'vitest'
import { apiKeyExpiryBounds, apiKeyStatus, clampApiKeyExpiry, filterApiKeys } from '@/pages/api-keys/apiKeyExpiry'
import type { ApiKeyInfo } from '@/domain/access'

const keys: ApiKeyInfo[] = [
  { id: '1', name: 'ci-pipeline', permissions: [], owner: 'admin@example.com', createdAt: null, expiresAt: '2025-02-10T00:00:00Z', revokedAt: null, lastUsedAt: null },
  { id: '2', name: 'old-key', permissions: [], owner: 'builder', createdAt: null, expiresAt: '2025-01-01T00:00:00Z', revokedAt: null, lastUsedAt: null },
  { id: '3', name: 'revoked-key', permissions: [], owner: 'admin@example.com', createdAt: null, expiresAt: null, revokedAt: '2025-01-02T00:00:00Z', lastUsedAt: null },
]

describe('admin view helpers', () => {
  it('bounds API key expiry from tomorrow through the configured maximum', () => {
    const bounds = apiKeyExpiryBounds(new Date('2025-01-31T12:00:00Z'), 60)
    expect(bounds).toEqual({ min: '2025-02-01', max: '2025-04-01', defaultValue: '2025-03-02' })
    expect(clampApiKeyExpiry('2025-01-15', bounds)).toBe(bounds.min)
    expect(clampApiKeyExpiry('2025-05-01', bounds)).toBe(bounds.max)
  })

  it('filters keys by name or creator and hides inactive keys by default', () => {
    const now = new Date('2025-01-15T00:00:00Z')
    expect(filterApiKeys(keys, '', true, now).map(key => key.id)).toEqual(['1'])
    expect(filterApiKeys(keys, 'BUILDER', false, now).map(key => key.id)).toEqual(['2'])
    expect(filterApiKeys(keys, 'ci-pip', false, now).map(key => key.id)).toEqual(['1'])
    expect(apiKeyStatus(keys[2], now)).toBe('revoked')
  })

})
