import {describe, expect, it} from 'vitest'
import {assetFamily, assetTypeName, findUnrecognizedAssets, withEnvironmentNames, withRecordDetails, type AssetInfo, type AssetType} from '@/domain/environment'

describe('unrecognized asset derivation', () => {
  it('keeps only undeleted, enabled database assets missing from the live environment', () => {
    const databaseAssets: AssetInfo[] = [
      {id: 'active', name: 'Active', type: 'container'},
      {id: 'unknown', name: 'Unknown', type: 'custom'},
      {id: 'deleted', name: 'Deleted', type: 'custom', deletedAt: '2026-01-01T00:00:00Z'},
      {id: 'disabled', name: 'Disabled', type: 'custom', disabledAt: '2026-01-01T00:00:00Z'},
    ]
    const liveAssets: AssetInfo[] = [{id: 'active', name: 'Active', type: {namespace: 'test', localName: 'container', name: 'Container', regime: 'MEASURED', family: 'KUBERNETES_CONTAINER'}}]

    expect(findUnrecognizedAssets(databaseAssets, liveAssets).map(asset => asset.id)).toEqual(['unknown'])
  })
})

describe('asset record details', () => {
  it('takes the owner of each live asset from its database record', () => {
    const databaseAssets: AssetInfo[] = [
      {id: 'east.cluster', name: 'Cluster', owner: 'east'},
      {id: 'local', name: 'Local'},
    ]
    const liveAssets: AssetInfo[] = [{id: 'east.cluster'}, {id: 'local'}, {id: 'missing'}]

    expect(withRecordDetails(liveAssets, databaseAssets).map(asset => asset.owner)).toEqual(['east', undefined, undefined])
  })

  it('takes the name of each live asset from its database record, live assets carrying none', () => {
    const databaseAssets: AssetInfo[] = [{id: 'cluster', name: 'Cluster'}]
    const liveAssets: AssetInfo[] = [{id: 'cluster'}, {id: 'missing'}]

    expect(withRecordDetails(liveAssets, databaseAssets).map(asset => asset.name)).toEqual(['Cluster', undefined])
  })
})

describe('asset type', () => {
  const llm: AssetType = {namespace: 'tech.illuin.wombat-module', localName: 'llm-static', name: 'tech.illuin.wombat-module.llm-static', regime: 'MODELED', family: 'LLM'}
  const types = new Map([[llm.name, llm]])

  it('reads the type name served as a string or in full', () => {
    expect(assetTypeName({id: 'a', type: llm.name})).toBe(llm.name)
    expect(assetTypeName({id: 'a', type: llm})).toBe(llm.name)
    expect(assetTypeName({id: 'a'})).toBeNull()
  })

  it('reads the family from the full type, or looks it up by name', () => {
    expect(assetFamily({id: 'a', type: {...llm, family: 'KUBERNETES_CONTAINER'}}, types)).toBe('KUBERNETES_CONTAINER')
    expect(assetFamily({id: 'a', type: llm.name}, types)).toBe('LLM')
    expect(assetFamily({id: 'a', type: 'tech.illuin.unknown'}, types)).toBe('UNKNOWN')
  })
})

describe('live environment names', () => {
  it('takes the name of each live environment from its record', () => {
    expect(withEnvironmentNames(
      [{id: 'my-environment', assets: []}, {id: 'unknown', assets: []}],
      [{id: 'my-environment', name: 'My Environment', assets: []}],
    )).toEqual([
      {id: 'my-environment', name: 'My Environment', assets: []},
      {id: 'unknown', name: null, assets: []},
    ])
  })
})
