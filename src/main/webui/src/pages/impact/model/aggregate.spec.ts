import { describe, expect, it } from 'vitest'
import { aggregate, lifetimeHours, totalValue } from '@/pages/impact/model/aggregate'
import type { AssetType } from '@/domain/environment'
import type { AssetImpact, Footprint, KubernetesImpact, LLMImpact } from '@/domain/impact'

const K8S_TYPE: AssetType = { namespace: 'tech.illuin.wombat-module', localName: 'kubernetes-api', name: 'tech.illuin.wombat-module.kubernetes-api', regime: 'MEASURED', family: 'KUBERNETES_CONTAINER' }
const LLM_TYPE: AssetType = { namespace: 'tech.illuin.wombat-module', localName: 'llm-static', name: 'tech.illuin.wombat-module.llm-static', regime: 'MODELED', family: 'LLM' }

function footprint(gwp: number, pe: number, adp: number): Footprint {
  const impact = (unit: string, value: number) => ({ unit, description: '', embedded: { value: 0 }, use: { value } })
  return { gwp: impact('kgCO2eq', gwp), pe: impact('MJ', pe), adp: impact('kgSbeq', adp) }
}

function llm(serviceId: string, fp: Footprint, tokens: number, requests: number, energy: number): LLMImpact {
  return {
    serviceId, assetType: LLM_TYPE, profile: {}, provider: 'mistralai', model: serviceId, location: 'FRA', footprint: fp,
    estimation: { impacts: { energy: { type: 'energy', name: 'Energy', value: { min: energy, max: energy }, unit: 'kWh' }, gwp: null, adpe: null, pe: null } },
    outputTokenCount: tokens, requestCount: requests,
  }
}

function k8s(serviceId: string, fp: Footprint): KubernetesImpact {
  return {
    serviceId, assetType: K8S_TYPE, footprint: fp, share: 0.5,
    profile: { provider: 'aws', instanceType: 'c5.large', location: 'FRA', lifespan: '5y' },
    location: { id: 'test-cluster', namespace: 'test-ns' },
    serverSpecification: { vcpu: 8, memory: 16, ssdStorage: null, hddStorage: null, gpuUnits: null, platform: null },
    loadPercent: 25, nodeCount: 1,
  }
}

function asset(id: string, serviceImpacts: AssetImpact['serviceImpacts']): AssetImpact {
  return { assetIdentity: { id, environmentId: 'test', name: id }, footprint: footprint(0, 0, 0), serviceImpacts, provider: 'BOAVIZTA', range: null }
}

const impacts = [
  asset('llm', [llm('mistral-large-latest', footprint(1.5, 3, 0.1), 1500, 10, 0.02), llm('mistral-small-latest', footprint(0.5, 1, 0.05), 500, 20, 0.01)]),
  asset('cluster', [k8s('api', footprint(4, 8, 0.2)), k8s('worker', footprint(2, 4, 0.1))]),
]
const assets = [{ id: 'llm', name: 'LLM asset', type: LLM_TYPE.name }, { id: 'cluster', name: 'Cluster', type: K8S_TYPE.name }]

describe('aggregate (EnvironmentImpact)', () => {
  it('consolidates a multi-service LLM asset and sums its totals', () => {
    const model = aggregate([impacts[0]], assets)
    expect(model.llmPanels).toHaveLength(1)
    const panel = model.llmPanels[0]
    expect(panel.name).toBe('LLM asset')
    expect(panel.serviceIds).toEqual(['mistral-large-latest', 'mistral-small-latest'])
    expect(panel.outputTokenCount).toBe(2000)
    expect(panel.requestCount).toBe(30)
    expect(panel.requestPerYear).toBe(30)
    expect(panel.gwpTotal.value).toBeCloseTo(2.0, 4)
    expect(panel.energyTotal).toEqual({ value: expect.closeTo(0.4, 6), unit: 'KWh' })
    expect(panel.gwpPerRequest.value).toBeCloseTo(2 / 30, 6)
    expect(panel.provider).toBe('mistralai')
    expect(panel.model).toBe('mistral-large-latest, mistral-small-latest')
    expect(panel.location).toBe('FRA')
  })

  it('splits Docker and LLM totals, sorts services by GWP and computes shares', () => {
    const model = aggregate(impacts, assets)
    expect(totalValue(model.globalImpact.gwp)).toBeCloseTo(8, 6)
    expect(totalValue(model.dockerImpact.gwp)).toBeCloseTo(6, 6)
    expect(totalValue(model.llmImpact.gwp)).toBeCloseTo(2, 6)
    expect(model.serviceImpacts.map(service => service.label)).toEqual([
      'Cluster / api', 'Cluster / worker', 'LLM asset / mistral-large-latest', 'LLM asset / mistral-small-latest',
    ])
    expect(model.impactShares.get('Cluster / api')).toBeCloseTo(0.5, 6)
    expect([...model.impactShares.values()].reduce((a, b) => a + b, 0)).toBeCloseTo(1, 6)
    expect(model.assetCount).toBe(2)
    expect(model.serviceCount).toBe(4)
    expect(model.dockerServiceCount).toBe(2)
    expect(model.llmServiceCount).toBe(2)
    expect(model.allServices).toEqual(['api', 'mistral-large-latest', 'mistral-small-latest', 'worker'])
  })

  it('builds the Kubernetes panel from the first impact', () => {
    const panel = aggregate(impacts, assets).kubernetesPanels[0]
    expect(panel.instanceType).toBe('c5.large')
    expect(panel.location).toBe('FRA')
    expect(panel.lifespan).toBe(43_800)
    expect(panel.clusters).toEqual([{ id: 'test-cluster', namespace: 'test-ns' }])
    expect(panel.load).toEqual({ cpuUsageCores: 2, loadPercent: 25, nodeCount: 1 })
    expect(panel.energyTotal.value).toBe(0)
  })

  it('does not read a kebab-case instance type from a server profile', () => {
    const impact = asset('cluster', [{
      ...k8s('api', footprint(1, 1, 1)),
      profile: { provider: 'aws', 'instance-type': 'legacy-c5.large' },
    }])

    expect(aggregate([impact], assets).kubernetesPanels[0].instanceType).toBeNull()
  })

  it('narrows to the requested services while keeping every service in the picker', () => {
    const model = aggregate(impacts, assets, ['worker', 'unknown'])
    expect(model.includedServices).toEqual(['worker'])
    expect(model.allServices).toHaveLength(4)
    expect(model.llmPanels).toEqual([])
    expect(totalValue(model.globalImpact.gwp)).toBeCloseTo(2, 6)
    expect(model.impactShares.get('Cluster / worker')).toBe(1)
  })

  it('returns zero footprints when nothing is evaluated', () => {
    const model = aggregate([], [])
    expect(model.globalImpact.gwp?.unit).toBe('kgCO2eq')
    expect(totalValue(model.globalImpact.gwp)).toBe(0)
    expect(model.assetPanels).toEqual([])
  })

  it('reads lifespans as Lifetime.asHours', () => {
    expect(lifetimeHours('43800')).toBe(43_800)
    expect(lifetimeHours('30d')).toBe(720)
    expect(lifetimeHours('1m')).toBe(730)
    expect(lifetimeHours(undefined)).toBe(0)
  })
})
