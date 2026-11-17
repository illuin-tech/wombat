import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import type { RouteLocationNormalized } from 'vue-router'
import {
  applyServices,
  datesQuery,
  filtersFor,
  importQueryServices,
  lastDashboardLocation,
  legacyRedirect,
  loadLastDashboard,
  loadServicesState,
  rememberDashboard,
  resetServices,
} from '@/pages/impact/model/useDashboardQuery'

function location(environment: string, query: RouteLocationNormalized['query']): RouteLocationNormalized {
  return { name: 'impact', params: { environment }, query, hash: '' } as unknown as RouteLocationNormalized
}

describe('dashboard query', () => {
  afterEach(resetServices)

  it('redirects legacy bookmarks to the environment path, keeping the query for the impact route to import', () => {
    expect(legacyRedirect({ environment: 'prod', from: '2026-01-01T00:00', services: ['a=x'] }, ['test', 'prod'])).toEqual({
      name: 'impact', params: { environment: 'prod' }, query: { from: '2026-01-01T00:00', services: ['a=x'] },
    })
  })

  it('falls back to the first environment', () => {
    expect(legacyRedirect({ environment: 'unknown' }, ['test', 'prod'])).toEqual({ name: 'impact', params: { environment: 'test' }, query: {} })
    expect(legacyRedirect({}, [])).toBeNull()
  })

  it('reads dates from the URL and services from the stored selection, scoped to their environment', () => {
    applyServices('test', ['a=x,y', 'b'])
    const filters = filtersFor('test', { from: '2026-01-01T00:00', to: null })
    expect(filters.from).toBe('2026-01-01T00:00')
    expect(filters.to).toBeNull()
    expect(filters.services.get('a')).toEqual(['x', 'y'])
    expect(filtersFor('prod', {}).services.size).toBe(0)
  })

  it('only puts the set dates in the query', () => {
    expect(datesQuery('2026-01-01T00:00', '')).toEqual({ from: '2026-01-01T00:00' })
    expect(datesQuery(null, null)).toEqual({})
  })

  it('caches the selection in session storage', async () => {
    applyServices('test', ['a=x'])
    await nextTick()
    expect(loadServicesState()).toEqual({ environment: 'test', services: ['a=x'] })
  })

  it('ignores missing or malformed cached selections', () => {
    const empty = { environment: null, services: [] }
    expect(loadServicesState({ getItem: () => null })).toEqual(empty)
    expect(loadServicesState({ getItem: () => '{not json' })).toEqual(empty)
    expect(loadServicesState({ getItem: () => JSON.stringify({ environment: 1, services: [] }) })).toEqual(empty)
    expect(loadServicesState({ getItem: () => { throw new Error('blocked') } })).toEqual(empty)
  })

  it('leads the header links back to the last dashboard looked at', async () => {
    rememberDashboard('prod', '2026-04-04T00:00', '2026-04-05T00:00')
    expect(lastDashboardLocation.value).toEqual({ name: 'impact', params: { environment: 'prod' }, query: { from: '2026-04-04T00:00', to: '2026-04-05T00:00' } })
    await nextTick()
    expect(loadLastDashboard()).toEqual({ environment: 'prod', from: '2026-04-04T00:00', to: '2026-04-05T00:00' })
  })

  it('falls back to the home redirect without a cached dashboard', () => {
    expect(loadLastDashboard({ getItem: () => null })).toEqual({ environment: null, from: null, to: null })
    expect(loadLastDashboard({ getItem: () => JSON.stringify({ environment: 'prod', from: 3, to: null }) })).toEqual({ environment: null, from: null, to: null })
  })

  it('moves services of old bookmarks out of the URL, keeping the dates', () => {
    expect(importQueryServices(location('test', { from: '2026-01-01T00:00', services: ['a=x'] }))).toEqual({
      name: 'impact', params: { environment: 'test' }, query: { from: '2026-01-01T00:00' }, hash: '', replace: true,
    })
    expect(filtersFor('test', {}).services.get('a')).toEqual(['x'])
    expect(importQueryServices(location('test', { from: '2026-01-01T00:00' }))).toBe(true)
  })
})
