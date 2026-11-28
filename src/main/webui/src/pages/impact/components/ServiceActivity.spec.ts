import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import type { KubernetesServiceActivity, LlmServiceActivity, ServiceActivity as Activity } from '@/pages/impact/model/serviceActivity'
import ServiceActivity from './ServiceActivity.vue'

const LLM: LlmServiceActivity = {
  kind: 'llm', provider: 'mistralai', model: 'mistral-large-latest', location: 'FRA',
  outputTokenCount: 5000, outputTokensPerDay: 500, requestCount: 20, requestsPerDay: 2, outputTokensPerRequest: 250,
}
const KUBERNETES: KubernetesServiceActivity = { kind: 'kubernetes', location: { id: 'test-cluster', namespace: 'test-ns' }, cpuShare: 0.25, cpuUsageCores: 0.5 }

function render(activity: Activity) {
  return mount(ServiceActivity, { props: { activity } })
}

function params(wrapper: ReturnType<typeof render>): [string, string][] {
  return wrapper.findAll('.param-item').map(item => [item.find('.param-key').text(), item.find('.param-val').text()])
}

describe('ServiceActivity', () => {
  it('shows the tokens and requests of an LLM service', () => {
    expect(params(render(LLM))).toEqual([
      ['Model', 'mistral-large-latest'], ['Provider', 'mistralai'], ['Location', 'FRA'],
      ['Output tokens', '5000'], ['Output tokens / day', '500'], ['Requests', '20'], ['Requests / day', '2'], ['Output tokens / request', '250'],
    ])
  })

  it('shows a dash for the means of an LLM service which cannot be told', () => {
    const wrapper = render({ ...LLM, provider: null, outputTokensPerDay: null, requestsPerDay: null, outputTokensPerRequest: null })
    expect(params(wrapper)).toContainEqual(['Output tokens / day', '—'])
    expect(params(wrapper).map(([key]) => key)).not.toContain('Provider')
  })

  it('shows the CPU of a Kubernetes service', () => {
    expect(params(render(KUBERNETES))).toEqual([['Cluster / Namespace', 'test-cluster / test-ns'], ['CPU share', '25.00%'], ['Mean CPU used', '0.5 cores']])
    expect(params(render({ ...KUBERNETES, location: null, cpuUsageCores: null }))).toEqual([['CPU share', '25.00%'], ['Mean CPU used', '—']])
  })
})
