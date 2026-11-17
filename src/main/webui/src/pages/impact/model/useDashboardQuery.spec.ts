import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import type { RouteLocationNormalized } from 'vue-router'
import {
  applyDates,
  applyFilters,
  dashboardSelection,
  filtersFor,
  importQueryFilters,
  legacyRedirect,
  loadSelection,
  resetFilters,
} from '@/pages/impact/model/useDashboardQuery'

function location(environment: string, query: RouteLocationNormalized['query']): RouteLocationNormalized {
  return { name: 'impact', params: { environment }, query, hash: '' } as unknown as RouteLocationNormalized
}

describe('dashboard query', () => {
  afterEach(resetFilters)

  it('redirects legacy bookmarks to the environment path, keeping the query for the impact route to import', () => {
    expect(legacyRedirect({ environment: 'prod', from: '2026-01-01T00:00', services: ['a=x'] }, ['test', 'prod'])).toEqual({
      name: 'impact', params: { environment: 'prod' }, query: { from: '2026-01-01T00:00', services: ['a=x'] },
    })
  })

  it('falls back to the first environment', () => {
    expect(legacyRedirect({ environment: 'unknown' }, ['test', 'prod'])).toEqual({ name: 'impact', params: { environment: 'test' }, query: {} })
    expect(legacyRedirect({}, [])).toBeNull()
  })

  it('keeps applied filters in memory and scopes services to their environment', () => {
    applyFilters('test', '2026-01-01T00:00', '', ['a=x,y', 'b'])
    const filters = filtersFor('test')
    expect(filters.from).toBe('2026-01-01T00:00')
    expect(filters.to).toBeNull()
    expect(filters.services.get('a')).toEqual(['x', 'y'])
    expect(filtersFor('prod').services.size).toBe(0)
    expect(filtersFor('prod').from).toBe('2026-01-01T00:00')
  })

  it('leaves the selection untouched when dates do not change', () => {
    applyFilters('test', '2026-01-01T00:00', null, ['a'])
    const before = dashboardSelection.value
    applyDates('2026-01-01T00:00', '')
    expect(dashboardSelection.value).toBe(before)
    applyDates('2026-02-01T00:00', null)
    expect(dashboardSelection.value).toEqual({ from: '2026-02-01T00:00', to: null, environment: 'test', services: ['a'] })
  })

  it('caches the selection in session storage', async () => {
    applyFilters('test', '2026-01-01T00:00', null, ['a=x'])
    await nextTick()
    expect(loadSelection()).toEqual({ from: '2026-01-01T00:00', to: null, environment: 'test', services: ['a=x'] })
  })

  it('ignores missing or malformed cached selections', () => {
    const empty = { from: null, to: null, environment: null, services: [] }
    expect(loadSelection({ getItem: () => null })).toEqual(empty)
    expect(loadSelection({ getItem: () => '{not json' })).toEqual(empty)
    expect(loadSelection({ getItem: () => JSON.stringify({ from: 1, services: [] }) })).toEqual(empty)
    expect(loadSelection({ getItem: () => { throw new Error('blocked') } })).toEqual(empty)
  })

  it('moves filters of old bookmarks out of the URL', () => {
    expect(importQueryFilters(location('test', { from: '2026-01-01T00:00', services: ['a=x'], other: 'kept' }))).toEqual({
      name: 'impact', params: { environment: 'test' }, query: { other: 'kept' }, hash: '', replace: true,
    })
    expect(filtersFor('test').from).toBe('2026-01-01T00:00')
    expect(filtersFor('test').services.get('a')).toEqual(['x'])
    expect(importQueryFilters(location('test', { other: 'kept' }))).toBe(true)
  })
})
