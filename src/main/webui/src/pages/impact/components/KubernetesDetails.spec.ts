import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import type { AssetType } from '@/domain/environment'
import type { KubernetesPanel } from '@/pages/impact/model/aggregate'
import KubernetesDetails from './KubernetesDetails.vue'

const type: AssetType = { namespace: 'tech.illuin.wombat-module', localName: 'kubernetes-api', name: 'tech.illuin.wombat-module.kubernetes-api', regime: 'MEASURED', family: 'KUBERNETES_CONTAINER' }
const zero = { value: 0, unit: 'kgCO2eq' }

function panel(overrides: Partial<KubernetesPanel>): KubernetesPanel {
  return {
    kind: 'kubernetes', name: 'Cluster', type, regime: 'MEASURED', serviceIds: ['api'],
    gwpTotal: zero, energyTotal: zero, peTotal: zero, adpeTotal: zero,
    provider: 'aws', instanceType: 'c5.large', location: 'FRA', lifespan: 43_800, profileOrigin: 'DECLARED', warnings: [],
    clusters: [], specification: { vcpu: 4, memory: 16, ssdStorage: null, hddStorage: null, gpuUnits: null, platform: null },
    load: { cpuUsageCores: 1, loadPercent: 25, nodeCount: 1 },
    ...overrides,
  }
}

function render(overrides: Partial<KubernetesPanel>) {
  return mount(KubernetesDetails, { props: { panel: panel(overrides) }, global: { stubs: { AssetTypeParam: true } } })
}

function keys(wrapper: ReturnType<typeof render>): string[] {
  return wrapper.findAll('.params-inline .param-key').map(key => key.text())
}

describe('KubernetesDetails', () => {
  it('shows a declared cloud profile in full', () => {
    const wrapper = render({})
    expect(wrapper.find('.profile-origin').text()).toBe('Declared')
    expect(keys(wrapper)).toEqual(['Provider', 'Instance type', 'Location', 'Lifespan', 'vCPU', 'Memory'])
    expect(wrapper.find('.profile-warnings').exists()).toBe(false)
  })

  it('tells an inferred bare server profile apart, with what it assumed', () => {
    const warnings = ['Sized after the resource quotas of namespaces payments: a slice of the cluster, not its nodes', 'No location set, the world average is assumed']
    const wrapper = render({ provider: null, instanceType: null, profileOrigin: 'RESOURCE_QUOTAS', warnings })
    expect(wrapper.find('.profile-origin').text()).toBe('Inferred from resource quotas')
    expect(keys(wrapper)).toEqual(['Location', 'Lifespan', 'vCPU', 'Memory'])
    expect(wrapper.findAll('.profile-warnings li').map(item => item.text())).toEqual(warnings)
  })

  it('shows an origin defined by another module as sent', () => {
    const wrapper = render({ provider: null, instanceType: null, profileOrigin: 'MODEL_CARD', warnings: [] })
    expect(wrapper.find('.profile-origin').text()).toBe('MODEL_CARD')
    expect(wrapper.find('.profile-origin').attributes('title')).toBe('The server profile is inferred (MODEL_CARD)')
  })
})
