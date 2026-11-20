import { assetFamily, type AssetType, type EnvironmentInfo, type ServiceFamily } from '@/domain/environment'

export type EnvironmentStatus = 'active' | 'disabled' | 'provisioned'

export const STATUS_LABELS: Record<EnvironmentStatus, string> = {
  active: 'Active',
  disabled: 'Disabled',
  provisioned: 'Provisioned',
}

export function environmentStatuses(environment: EnvironmentInfo): EnvironmentStatus[] {
  const statuses: EnvironmentStatus[] = [environment.disabledAt ? 'disabled' : 'active']
  if (environment.provisioned) statuses.push('provisioned')
  return statuses
}

/** The environments whose id, name or description contains the query, and which have one of the statuses, if any. */
export function filterEnvironments(environments: readonly EnvironmentInfo[], query: string, statuses: readonly EnvironmentStatus[]): EnvironmentInfo[] {
  const needle = query.trim().toLowerCase()
  return environments.filter(environment => {
    if (statuses.length > 0 && !environmentStatuses(environment).some(status => statuses.includes(status)))
      return false
    if (!needle)
      return true
    return [environment.id, environment.name, environment.description]
      .some(text => text?.toLowerCase().includes(needle))
  })
}

export interface FamilyCount {
  family: ServiceFamily
  count: number
}

const FAMILY_ORDER: ServiceFamily[] = ['KUBERNETES_CONTAINER', 'LLM', 'UNKNOWN']

/** How many assets the environment has per family, leaving out the families it has none of. */
export function familyCounts(environment: EnvironmentInfo, typesByName: ReadonlyMap<string, AssetType>): FamilyCount[] {
  const counts = new Map<ServiceFamily, number>()
  for (const asset of environment.assets) {
    const family = assetFamily(asset, typesByName)
    counts.set(family, (counts.get(family) ?? 0) + 1)
  }
  return FAMILY_ORDER.filter(family => counts.has(family)).map(family => ({ family, count: counts.get(family)! }))
}
