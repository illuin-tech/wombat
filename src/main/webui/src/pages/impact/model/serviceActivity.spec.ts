import { describe, expect, it } from 'vitest'
import { rangeHours, serviceActivity } from '@/pages/impact/model/serviceActivity'
import type { AssetType } from '@/domain/environment'
import type { Footprint, KubernetesImpact, LLMImpact, TimeRangeJson } from '@/domain/impact'

const K8S_TYPE: AssetType = { namespace: 'tech.illuin.wombat-module', localName: 'kubernetes-api', name: 'tech.illuin.wombat-module.kubernetes-api', regime: 'MEASURED', family: 'KUBERNETES_CONTAINER' }
const LLM_TYPE: AssetType = { namespace: 'tech.illuin.wombat-module', localName: 'llm-static', name: 'tech.illuin.wombat-module.llm-static', regime: 'MODELED', family: 'LLM' }
const FOOTPRINT: Footprint = { gwp: null, pe: null, adp: null, energy: null }
const TEN_DAYS: TimeRangeJson = { start: '2026-01-01T00:00:00Z', end: '2026-01-11T00:00:00Z' }

function llm(outputTokenCount: number, requestCount: number): LLMImpact {
  return {
    serviceId: 'chat', assetType: LLM_TYPE, profile: {}, provider: 'mistralai', model: 'mistral-large-latest', location: 'FRA', footprint: FOOTPRINT,
    outputTokenCount, requestCount,
  }
}

function k8s(share: number, vcpu: number | null): KubernetesImpact {
  return {
    serviceId: 'api', assetType: K8S_TYPE, footprint: FOOTPRINT, share, profile: null,
    location: { id: 'test-cluster', namespace: 'test-ns' },
    serverSpecification: { vcpu, memory: 16, ssdStorage: null, hddStorage: null, gpuUnits: null, platform: null },
    loadPercent: 25, nodeCount: 1,
  }
}

describe('serviceActivity', () => {
  it('averages the output tokens and requests of an LLM service per day and per request', () => {
    expect(serviceActivity(llm(5000, 20), TEN_DAYS)).toEqual({
      kind: 'llm', provider: 'mistralai', model: 'mistral-large-latest', location: 'FRA',
      outputTokenCount: 5000, outputTokensPerDay: 500, requestCount: 20, requestsPerDay: 2, outputTokensPerRequest: 250,
    })
  })

  it('leaves out the means per day when the period is unknown, empty or malformed', () => {
    for (const range of [null, { start: TEN_DAYS.start, end: TEN_DAYS.start }, { start: 'not a date', end: TEN_DAYS.end }]) {
      const activity = serviceActivity(llm(5000, 20), range)
      expect(activity).toMatchObject({ outputTokensPerDay: null, requestsPerDay: null, outputTokensPerRequest: 250 })
    }
  })

  it('leaves out the tokens per request of an LLM service without requests', () => {
    expect(serviceActivity(llm(0, 0), TEN_DAYS)).toMatchObject({ outputTokensPerDay: 0, requestsPerDay: 0, outputTokensPerRequest: null })
  })

  it('takes the share of its asset cores for a Kubernetes service', () => {
    /* 25 % of 8 vCPU is 2 cores for the asset */
    expect(serviceActivity(k8s(0.25, 8), TEN_DAYS)).toEqual({
      kind: 'kubernetes', location: { id: 'test-cluster', namespace: 'test-ns' }, cpuShare: 0.25, cpuUsageCores: 0.5,
    })
  })

  it('leaves out the cores of a Kubernetes service when its asset vCPU is unknown', () => {
    expect(serviceActivity(k8s(0.25, null), TEN_DAYS)).toMatchObject({ cpuShare: 0.25, cpuUsageCores: null })
    expect(serviceActivity(k8s(0.25, 0), TEN_DAYS)).toMatchObject({ cpuShare: 0.25, cpuUsageCores: null })
  })
})

describe('rangeHours', () => {
  it('measures a period, and an unknown one as empty', () => {
    expect(rangeHours(TEN_DAYS)).toBe(240)
    expect(rangeHours(null)).toBe(0)
  })
})
