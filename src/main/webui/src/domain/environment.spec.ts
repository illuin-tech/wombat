import {describe, expect, it} from 'vitest'
import {findUnrecognizedAssets, type AssetInfo} from '@/domain/environment'

describe('unrecognized asset derivation', () => {
  it('keeps only undeleted database assets missing from the live environment', () => {
    const databaseAssets: AssetInfo[] = [
      {id: 'active', name: 'Active', type: 'container'},
      {id: 'unknown', name: 'Unknown', type: 'custom'},
      {id: 'deleted', name: 'Deleted', type: 'custom', deletedAt: '2026-01-01T00:00:00Z'},
    ]
    const liveAssets: AssetInfo[] = [{id: 'active', name: 'Active', type: {namespace: 'test', localName: 'container', name: 'Container', regime: 'MEASURED', family: 'KUBERNETES_CONTAINER'}}]

    expect(findUnrecognizedAssets(databaseAssets, liveAssets).map(asset => asset.id)).toEqual(['unknown'])
  })
})
