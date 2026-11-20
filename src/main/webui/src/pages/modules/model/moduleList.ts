import { FAMILY_ORDER } from '@/domain/assetTypeDisplay'
import type { ModuleInfo } from '@/domain/module'

export type ModuleState = 'enabled' | 'disabled' | 'pending' | 'missing'

export const STATE_LABELS: Record<ModuleState, string> = {
  enabled: 'Enabled',
  disabled: 'Disabled',
  pending: 'Not deployed',
  missing: 'Missing',
}

export function moduleStates(module: ModuleInfo): ModuleState[] {
  if (module.missing) return ['missing']
  const states: ModuleState[] = [module.enabled ? 'enabled' : 'disabled']
  if (module.pending) states.push('pending')
  return states
}

/** The modules whose name contains the query, and which have one of the states, if any. */
export function filterModules(modules: readonly ModuleInfo[], query: string, states: readonly ModuleState[]): ModuleInfo[] {
  const needle = query.trim().toLowerCase()
  return modules.filter(module => {
    if (states.length > 0 && !moduleStates(module).some(state => states.includes(state)))
      return false
    return !needle || module.type.name.toLowerCase().includes(needle)
  })
}

/** Missing modules last; the others by family, then name. */
export function sortModules(modules: readonly ModuleInfo[]): ModuleInfo[] {
  return [...modules].sort((a, b) =>
    Number(a.missing) - Number(b.missing)
    || FAMILY_ORDER.indexOf(a.type.family) - FAMILY_ORDER.indexOf(b.type.family)
    || a.type.localName.localeCompare(b.type.localName))
}
