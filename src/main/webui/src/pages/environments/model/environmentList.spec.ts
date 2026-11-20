import { describe, expect, it } from 'vitest'
import type { AssetType, EnvironmentInfo } from '@/domain/environment'
import { familyCounts, filterEnvironments } from './environmentList'
import { idError, slugify } from './identifiers'

const llm: AssetType = { namespace: 'n', localName: 'llm-static', name: 'n.llm-static', regime: 'MODELED', family: 'LLM' }
const kube: AssetType = { namespace: 'n', localName: 'kubernetes-static', name: 'n.kubernetes-static', regime: 'MODELED', family: 'KUBERNETES_CONTAINER' }
const types = new Map([[llm.name, llm], [kube.name, kube]])

const environments: EnvironmentInfo[] = [
  { id: 'preprod', name: 'Pre-production', description: 'Staging cluster', provisioned: true, assets: [] },
  { id: 'lab', name: 'Lab', disabledAt: '2026-01-01T00:00:00Z', assets: [] },
  { id: 'prod', name: 'Production', assets: [] },
]

describe('environment list', () => {
  it('filters by id, name or description', () => {
    expect(filterEnvironments(environments, 'PROD', []).map(env => env.id)).toEqual(['preprod', 'prod'])
    expect(filterEnvironments(environments, 'staging', []).map(env => env.id)).toEqual(['preprod'])
    expect(filterEnvironments(environments, '  ', []).map(env => env.id)).toEqual(['preprod', 'lab', 'prod'])
  })

  it('keeps the environments having one of the statuses', () => {
    expect(filterEnvironments(environments, '', ['disabled']).map(env => env.id)).toEqual(['lab'])
    expect(filterEnvironments(environments, '', ['provisioned']).map(env => env.id)).toEqual(['preprod'])
    expect(filterEnvironments(environments, '', ['active']).map(env => env.id)).toEqual(['preprod', 'prod'])
    expect(filterEnvironments(environments, '', ['disabled', 'provisioned']).map(env => env.id)).toEqual(['preprod', 'lab'])
  })

  it('counts assets per family', () => {
    const environment: EnvironmentInfo = {
      id: 'e', name: 'E',
      assets: [{ id: 'a', type: llm.name }, { id: 'b', type: kube.name }, { id: 'c', type: llm }, { id: 'd', type: 'n.gone' }],
    }
    expect(familyCounts(environment, types)).toEqual([
      { family: 'KUBERNETES_CONTAINER', count: 1 },
      { family: 'LLM', count: 2 },
      { family: 'UNKNOWN', count: 1 },
    ])
    expect(familyCounts({ id: 'empty', name: null, assets: [] }, types)).toEqual([])
  })
})

describe('identifiers', () => {
  it('derives an id from a name', () => {
    expect(slugify('  My Cluster (Préprod)! ')).toBe('my-cluster-preprod')
    expect(slugify('a_b--c')).toBe('a_b-c')
    expect(slugify('x'.repeat(80))).toHaveLength(64)
  })

  it('explains why an id is refused', () => {
    expect(idError('my-env_2')).toBeNull()
    expect(idError('')).toMatch(/required/)
    expect(idError('My Env')).toMatch(/Lowercase/)
    expect(idError('new')).toMatch(/reserved/)
    expect(idError('x'.repeat(65))).toMatch(/64/)
  })
})
