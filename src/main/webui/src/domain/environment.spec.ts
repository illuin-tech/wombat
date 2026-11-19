import {describe, expect, it} from 'vitest'
import {findUnrecognizedAssets, withRecordDetails, type AssetInfo} from '@/domain/environment'

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
