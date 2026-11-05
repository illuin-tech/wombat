import { computed } from 'vue'
import { useRoute, useRouter, type LocationQuery, type LocationQueryRaw, type RouteLocationRaw } from 'vue-router'
import { encodeServices, parseServices, type ServicesSelection } from '@/pages/impact/model/servicesQuery'

export interface DashboardFilters {
  environment: string
  from: string | null
  to: string | null
  services: ServicesSelection
}

function single(value: LocationQuery[string] | undefined): string | null {
  const first = Array.isArray(value) ? value[0] : value
  return first ?? null
}

function many(value: LocationQuery[string] | undefined): string[] {
  if (value === undefined || value === null) return []
  return (Array.isArray(value) ? value : [value]).filter((entry): entry is string => entry !== null)
}

export function filtersFromRoute(environment: string, query: LocationQuery): DashboardFilters {
  return {
    environment,
    from: single(query.from),
    to: single(query.to),
    services: parseServices(many(query.services)),
  }
}

export function filtersToLocation(filters: DashboardFilters): RouteLocationRaw {
  const query: LocationQueryRaw = {}
  if (filters.from) query.from = filters.from
  if (filters.to) query.to = filters.to
  const services = encodeServices(filters.services)
  if (services.length > 0) query.services = services
  return { name: 'impact', params: { environment: filters.environment }, query }
}

/** Where `/` and legacy `/?environment=x&from=…` bookmarks land. */
export function legacyRedirect(query: LocationQuery, environmentIds: string[]): RouteLocationRaw | null {
  const requested = single(query.environment)
  const environment = requested && environmentIds.includes(requested) ? requested : environmentIds[0]
  if (!environment) return null
  const { environment: _ignored, ...rest } = query
  return { name: 'impact', params: { environment }, query: rest }
}

/** Two-way binding between the dashboard route and its filters. */
export function useDashboardQuery() {
  const route = useRoute()
  const router = useRouter()

  const filters = computed(() => filtersFromRoute(String(route.params.environment ?? ''), route.query))

  function apply(next: DashboardFilters): Promise<unknown> {
    return router.push(filtersToLocation(next))
  }

  function reset(environment: string = filters.value.environment): Promise<unknown> {
    return router.push({ name: 'impact', params: { environment } })
  }

  return { filters, apply, reset }
}
