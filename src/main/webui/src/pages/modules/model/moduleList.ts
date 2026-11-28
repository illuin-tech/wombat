import type { ModuleFeature, ModuleInfo, ModuleSource } from '@/domain/module'

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
  profile: 'Profile',
  impact: 'Impact',
  cost: 'Cost',
}

/** What each kind of component does, as its pill explains it on hover. */
export const FEATURE_HINTS: Record<ModuleFeature, string> = {
  source: 'Samples the assets: polls the system they run on for their metrics, which are stored and read back when evaluating them.',
  activity: 'Resolves what the assets did over a time range, e.g. the CPU and memory they used, or their requests and tokens: read back from the sampled metrics, queried from a provider, or modeled from a declared profile.',
  profile: 'Infers the profile the assets are evaluated with, e.g. the hardware of the servers they run on, when their declared profile asks for it.',
  impact: 'Estimates the environmental impacts of that activity, e.g. greenhouse gas emissions, primary energy and the depletion of resources.',
  cost: 'Estimates the cost of that activity.',
}

export const SOURCE_HINTS: Record<ModuleSource, string> = {
  core: 'Shipped with Wombat.',
  extension: 'Loaded at startup from a jar of the extension folder (wombat.module.extension.path).',
}

export const FAMILY_MODULE_HINT = 'A family module defines no asset type: it serves every asset of its family, e.g. an impact methodology. Its components are tried after those of each asset\'s own module, in the order of the modules.'

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
