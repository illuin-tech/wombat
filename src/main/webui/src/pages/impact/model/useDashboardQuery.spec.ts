import { describe, expect, it } from 'vitest'
import { filtersFromRoute, filtersToLocation, legacyRedirect } from '@/pages/impact/model/useDashboardQuery'

describe('dashboard query', () => {
  it('redirects legacy bookmarks to the environment path, keeping the filters', () => {
    expect(legacyRedirect({ environment: 'prod', from: '2026-01-01T00:00', services: ['a=x'] }, ['test', 'prod'])).toEqual({
      name: 'impact', params: { environment: 'prod' }, query: { from: '2026-01-01T00:00', services: ['a=x'] },
    })
  })

  it('falls back to the first environment', () => {
    expect(legacyRedirect({ environment: 'unknown' }, ['test', 'prod'])).toEqual({ name: 'impact', params: { environment: 'test' }, query: {} })
    expect(legacyRedirect({}, [])).toBeNull()
  })

  it('round-trips filters through the route', () => {
    const filters = filtersFromRoute('test', { from: '2026-01-01T00:00', to: null, services: ['a=x,y', 'b'] })
    expect(filters.services.get('a')).toEqual(['x', 'y'])
    expect(filtersToLocation(filters)).toEqual({
      name: 'impact', params: { environment: 'test' }, query: { from: '2026-01-01T00:00', services: ['a=x,y', 'b'] },
    })
  })
})
