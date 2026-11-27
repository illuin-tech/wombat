import { describe, expect, it } from 'vitest'
import { evaluationFailures } from '@/domain/impact'
import { failedAssets } from '@/pages/impact/model/failures'

const K8S = 'tech.illuin.wombat-module.kubernetes-api'
const REASON = 'Cannot infer the server profile: nodes unreadable (forbidden to list nodes); no CPU quota in namespace payments; pods unreadable (forbidden to list pods)'

describe('evaluationFailures', () => {
  it('reads the failures of the status metadata', () => {
    const failures = [{ assetIdentity: { id: 'cluster', environmentId: 'prod', type: K8S }, reason: REASON }]
    expect(evaluationFailures({ reason: 'no-data', failures })).toEqual(failures)
  })

  it('has none when the metadata tells none', () => {
    expect(evaluationFailures({})).toEqual([])
    expect(evaluationFailures({ failures: 'none' })).toEqual([])
  })

  it('leaves malformed entries out', () => {
    const valid = { assetIdentity: { id: 'cluster', environmentId: 'prod', type: K8S }, reason: REASON }
    expect(evaluationFailures({ failures: [null, { reason: REASON }, { assetIdentity: { id: 'x' } }, valid] })).toEqual([valid])
  })
})

describe('failedAssets', () => {
  const failures = [
    { assetIdentity: { id: 'cluster', environmentId: 'prod', type: K8S }, reason: REASON },
    { assetIdentity: { id: 'other', environmentId: 'staging', type: K8S }, reason: 'Boom' },
  ]

  it('names the assets and their environments when known, otherwise by their ids', () => {
    expect(failedAssets(failures, [{ id: 'cluster' }, { id: 'cluster', name: 'Payments cluster' }], [{ id: 'prod', name: 'Production' }])).toEqual([
      { id: 'cluster', name: 'Payments cluster', environment: 'Production', reason: REASON },
      { id: 'other', name: 'other', environment: 'staging', reason: 'Boom' },
    ])
  })
})
