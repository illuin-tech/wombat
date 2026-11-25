import type { ModuleFeature, ModuleInfo } from '@/domain/module'

export type ModuleState = 'enabled' | 'disabled' | 'pending' | 'missing'

export const STATE_LABELS: Record<ModuleState, string> = {
  enabled: 'Enabled',
  disabled: 'Disabled',
  pending: 'Not deployed',
  missing: 'Missing',
}

export const FEATURE_LABELS: Record<ModuleFeature, string> = {
  source: 'Source',
  activity: 'Activity',
  impact: 'Impact',
  cost: 'Cost',
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

/**
 * The order of the modules once the one at `index` moves up (-1) or down (1), as `setModuleOrder` takes it: unchanged when
 * it cannot move further.
 */
export function moveModule(modules: readonly ModuleInfo[], index: number, offset: -1 | 1): string[] {
  const names = modules.map(module => module.type.name)
  const target = index + offset
  if (target < 0 || target >= names.length) return names
  names.splice(target, 0, ...names.splice(index, 1))
  return names
}
