import { describe, expect, it } from 'vitest'
import type { ModuleInfo } from '@/domain/module'
import { filterModules, moduleStates, moveModule } from './moduleList'

function module(localName: string, family: ModuleInfo['type']['family'], overrides: Partial<Omit<ModuleInfo, 'kind' | 'type'>> = {}): ModuleInfo {
  return {
    kind: 'asset',
    type: { namespace: 'tech.illuin', localName, name: `tech.illuin.${localName}`, regime: 'MEASURED', family },
    assetClass: 'Asset', enabled: true, creatable: true, deployed: true, pending: false, missing: false, source: 'core', ordering: null, provides: [], assetCount: 0,
    ...overrides,
  }
}

const anthropic = module('llm-anthropic', 'LLM')
const kubernetes = module('kubernetes-api', 'KUBERNETES_CONTAINER', { enabled: false, pending: true })
const removed = module('acme-removed', 'LLM', { missing: true, assetClass: null, source: null, deployed: false })

describe('moduleStates', () => {
  it('tells the registry state, and whether it awaits a redeploy', () => {
    expect(moduleStates(anthropic)).toEqual(['enabled'])
    expect(moduleStates(kubernetes)).toEqual(['disabled', 'pending'])
  })

  it('only tells a missing module is missing', () => {
    expect(moduleStates(removed)).toEqual(['missing'])
  })
})

describe('filterModules', () => {
  it('matches the query against the full type name', () => {
    expect(filterModules([anthropic, kubernetes], 'KUBERNETES', [])).toEqual([kubernetes])
    expect(filterModules([anthropic, kubernetes], 'tech.illuin.llm', [])).toEqual([anthropic])
  })

  it('keeps the modules with one of the states', () => {
    expect(filterModules([anthropic, kubernetes, removed], '', ['pending', 'missing'])).toEqual([kubernetes, removed])
  })
})

describe('moveModule', () => {
  const names = (...modules: ModuleInfo[]) => modules.map(module => module.type.name)

  it('swaps a module with the one above, or below', () => {
    expect(moveModule([anthropic, kubernetes, removed], 1, -1)).toEqual(names(kubernetes, anthropic, removed))
    expect(moveModule([anthropic, kubernetes, removed], 1, 1)).toEqual(names(anthropic, removed, kubernetes))
  })

  it('leaves the order as it is past either end', () => {
    expect(moveModule([anthropic, kubernetes], 0, -1)).toEqual(names(anthropic, kubernetes))
    expect(moveModule([anthropic, kubernetes], 1, 1)).toEqual(names(anthropic, kubernetes))
  })
})
