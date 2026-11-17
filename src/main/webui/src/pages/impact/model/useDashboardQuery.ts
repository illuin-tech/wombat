import { readonly, ref, watch } from 'vue'
import type { LocationQuery, RouteLocationNormalized, RouteLocationRaw } from 'vue-router'
import { parseServices, type ServicesSelection } from '@/pages/impact/model/servicesQuery'

export interface DashboardFilters {
  environment: string
  from: string | null
  to: string | null
  services: ServicesSelection
}

/** Filters picked on the dashboard, kept in memory and session storage. */
export interface DashboardSelection {
  from: string | null
  to: string | null
  environment: string | null
  services: string[]
}

const FILTER_KEYS = ['from', 'to', 'services'] as const
const STORAGE_KEY = 'wombat.dashboard.filters'

function emptySelection(): DashboardSelection {
  return { from: null, to: null, environment: null, services: [] }
}

function nullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string'
}

/** Reads the selection cached for this tab, so a reload keeps the filters. */
export function loadSelection(storage?: Pick<Storage, 'getItem'>): DashboardSelection {
  try {
    const source = storage ?? globalThis.sessionStorage
    const parsed: unknown = JSON.parse(source?.getItem(STORAGE_KEY) ?? 'null')
    if (!parsed || typeof parsed !== 'object') return emptySelection()
    const { from, to, environment, services } = parsed as Record<string, unknown>
    if (!nullableString(from) || !nullableString(to) || !nullableString(environment)) return emptySelection()
    if (!Array.isArray(services) || !services.every(entry => typeof entry === 'string')) return emptySelection()
    return { from, to, environment, services }
  } catch {
    return emptySelection()
  }
}

const selection = ref<DashboardSelection>(loadSelection())

watch(selection, (current) => {
  try {
    globalThis.sessionStorage?.setItem(STORAGE_KEY, JSON.stringify(current))
  } catch {
    // Storage unavailable (private mode, quota): filters simply won't survive a reload.
  }
})

export const dashboardSelection = readonly(selection)

function single(value: LocationQuery[string] | undefined): string | null {
  const first = Array.isArray(value) ? value[0] : value
  return first ?? null
}

function many(value: LocationQuery[string] | undefined): string[] {
  if (value === undefined || value === null) return []
  return (Array.isArray(value) ? value : [value]).filter((entry): entry is string => entry !== null)
}

export function filtersFor(environment: string, current: DashboardSelection = selection.value): DashboardFilters {
  return {
    environment,
    from: current.from,
    to: current.to,
    services: parseServices(current.environment === environment ? current.services : []),
  }
}

export function applyFilters(environment: string, from: string | null, to: string | null, services: string[]): void {
  selection.value = { from: from || null, to: to || null, environment, services }
}

export function applyDates(from: string | null, to: string | null): void {
  if ((from || null) === selection.value.from && (to || null) === selection.value.to) return
  selection.value = { ...selection.value, from: from || null, to: to || null }
}

export function resetFilters(): void {
  selection.value = emptySelection()
}

/** Route guard moving filters of bookmarks predating the in-memory selection out of the URL. */
export function importQueryFilters(to: RouteLocationNormalized): RouteLocationRaw | true {
  if (!FILTER_KEYS.some(key => key in to.query)) return true
  const environment = String(to.params.environment ?? '')
  applyFilters(environment, single(to.query.from), single(to.query.to), many(to.query.services))
  const query = Object.fromEntries(Object.entries(to.query).filter(([key]) => !(FILTER_KEYS as readonly string[]).includes(key)))
  return { name: 'impact', params: { environment }, query, hash: to.hash, replace: true }
}

/** Where `/` and legacy `/?environment=x&from=…` bookmarks land. */
export function legacyRedirect(query: LocationQuery, environmentIds: string[]): RouteLocationRaw | null {
  const requested = single(query.environment)
  const environment = requested && environmentIds.includes(requested) ? requested : environmentIds[0]
  if (!environment) return null
  const { environment: _ignored, ...rest } = query
  return { name: 'impact', params: { environment }, query: rest }
}
