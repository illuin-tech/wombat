import { describe, expect, it } from 'vitest'
import { assetTypesByName, isAssetModule, type ModuleMetadata } from './module'

const prometheus: ModuleMetadata = {
  kind: 'asset',
  type: { namespace: 'tech.illuin.wombat-module', localName: 'llm-prometheus', name: 'tech.illuin.wombat-module.llm-prometheus', regime: 'MEASURED', family: 'LLM' },
  assetClass: 'LLMPrometheusAsset', enabled: true, creatable: true,
}
const ecologits: ModuleMetadata = {
  kind: 'family',
  type: { namespace: 'tech.illuin.wombat-module', localName: 'ecologits', name: 'tech.illuin.wombat-module.ecologits', family: 'LLM' },
  assetClass: null, enabled: true, creatable: false,
}

describe('family modules', () => {
  it('define no asset type', () => {
    expect(isAssetModule(prometheus)).toBe(true)
    expect(isAssetModule(ecologits)).toBe(false)
    expect([...assetTypesByName([prometheus, ecologits]).keys()]).toEqual(['tech.illuin.wombat-module.llm-prometheus'])
  })
})
