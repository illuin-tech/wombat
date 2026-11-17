import { computed, readonly, ref, watch } from 'vue'
import type { LocationQuery, LocationQueryRaw, RouteLocationNormalized, RouteLocationRaw } from 'vue-router'
import { parseServices, type ServicesSelection } from '@/pages/impact/model/servicesQuery'

export interface DashboardFilters {
  environment: string
  from: string | null
  to: string | null
  services: ServicesSelection
}

/**
 * Assets and services picked on the dashboard, kept in memory and session storage rather than in the URL, which only
 * carries the environment (path) and the date range (`from` / `to` query).
 */
export interface ServicesState {
  /** Environment the `services` were picked for; they are ignored on any other one. */
  environment: string | null
  services: string[]
}

/** Last dashboard the user looked at, so the header links bring them back to it. */
export interface LastDashboard {
  environment: string | null
  from: string | null
  to: string | null
}

const SERVICES_KEY = 'wombat.dashboard.services'
const LAST_DASHBOARD_KEY = 'wombat.dashboard.last'

function emptyState(): ServicesState {
  return { environment: null, services: [] }
}

function emptyLastDashboard(): LastDashboard {
  return { environment: null, from: null, to: null }
}

function nullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string'
}

/** Reads a value cached for this tab, falling back to `empty` when it is missing, malformed or storage is blocked. */
function readStored<T>(key: string, parse: (value: Record<string, unknown>) => T | null, empty: () => T, storage?: Pick<Storage, 'getItem'>): T {
  try {
    const source = storage ?? globalThis.sessionStorage
    const parsed: unknown = JSON.parse(source?.getItem(key) ?? 'null')
    return (parsed && typeof parsed === 'object' ? parse(parsed as Record<string, unknown>) : null) ?? empty()
  } catch {
    return empty()
  }
}

function persist<T>(key: string, value: T): void {
  try {
    globalThis.sessionStorage?.setItem(key, JSON.stringify(value))
  } catch {
    // Storage unavailable (private mode, quota): the value simply won't survive a reload.
  }
}

export function loadServicesState(storage?: Pick<Storage, 'getItem'>): ServicesState {
  return readStored(SERVICES_KEY, ({ environment, services }) =>
    nullableString(environment) && Array.isArray(services) && services.every(entry => typeof entry === 'string')
      ? { environment, services }
      : null, emptyState, storage)
}

export function loadLastDashboard(storage?: Pick<Storage, 'getItem'>): LastDashboard {
  return readStored(LAST_DASHBOARD_KEY, ({ environment, from, to }) =>
    nullableString(environment) && nullableString(from) && nullableString(to) ? { environment, from, to } : null,
  emptyLastDashboard, storage)
}

const state = ref<ServicesState>(loadServicesState())
const lastDashboard = ref<LastDashboard>(loadLastDashboard())

watch(state, current => persist(SERVICES_KEY, current))
watch(lastDashboard, current => persist(LAST_DASHBOARD_KEY, current))

export const servicesState = readonly(state)

function single(value: LocationQuery[string] | undefined): string | null {
  const first = Array.isArray(value) ? value[0] : value
  return first ?? null
}

function many(value: LocationQuery[string] | undefined): string[] {
  if (value === undefined || value === null) return []
  return (Array.isArray(value) ? value : [value]).filter((entry): entry is string => entry !== null)
}

export function filtersFor(environment: string, query: LocationQuery, current: ServicesState = state.value): DashboardFilters {
  return {
    environment,
    from: single(query.from),
    to: single(query.to),
    services: parseServices(current.environment === environment ? current.services : []),
  }
}

export function datesQuery(from: string | null, to: string | null): LocationQueryRaw {
  const query: LocationQueryRaw = {}
  if (from) query.from = from
  if (to) query.to = to
  return query
}

export function applyServices(environment: string, services: string[]): void {
  state.value = { environment, services }
}

export function resetServices(): void {
  state.value = emptyState()
}

export function rememberDashboard(environment: string, from: string | null, to: string | null): void {
  const current = lastDashboard.value
  if (current.environment === environment && current.from === from && current.to === to) return
  lastDashboard.value = { environment, from, to }
}

/** Where the header links lead: the last dashboard looked at, or the default one through `/`. */
export const lastDashboardLocation = computed<RouteLocationRaw>(() => {
  const { environment, from, to } = lastDashboard.value
  return environment ? { name: 'impact', params: { environment }, query: datesQuery(from, to) } : { name: 'home' }
})

/** Route guard moving the `services` of bookmarks predating the stored selection out of the URL. */
export function importQueryServices(to: RouteLocationNormalized): RouteLocationRaw | true {
  if (!('services' in to.query)) return true
  const environment = String(to.params.environment ?? '')
  applyServices(environment, many(to.query.services))
  const { services: _ignored, ...query } = to.query
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
