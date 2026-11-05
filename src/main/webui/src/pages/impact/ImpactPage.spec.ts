import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { EnvironmentInfo, LiveEnvironment } from '@/domain/environment'
import type { AssetImpact, TimelineResponse } from '@/domain/impact'
import App from '@/App.vue'
import { router } from '@/router'

const api = vi.hoisted(() => ({
  fetchAppInfo: vi.fn(),
  fetchLiveEnvironments: vi.fn(),
  fetchEnvironments: vi.fn(),
  fetchImpact: vi.fn(),
  fetchTimeline: vi.fn(),
}))

vi.mock('@/services/api/app', () => ({ fetchAppInfo: api.fetchAppInfo }))
vi.mock('@/services/api/environments', () => ({
  fetchLiveEnvironments: api.fetchLiveEnvironments,
  fetchEnvironments: api.fetchEnvironments,
}))
vi.mock('@/services/api/impact', () => ({
  fetchImpact: api.fetchImpact,
  fetchTimeline: api.fetchTimeline,
}))

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

describe('ImpactPage progressive loading', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders the filter shell immediately and resolves timeline and impact modules independently', async () => {
    const live = deferred<LiveEnvironment[]>()
    const persisted = deferred<EnvironmentInfo[]>()
    const impact = deferred<AssetImpact[]>()
    const timeline = deferred<TimelineResponse>()
    api.fetchAppInfo.mockResolvedValue({
      auth: { enabled: false },
      user: { authenticated: false, name: null, roles: [], permissions: ['ui.dashboard.read'] },
      ui: { maxDateRange: { millis: 90 * 86_400_000, label: '90 days' } },
    })
    api.fetchLiveEnvironments.mockReturnValue(live.promise)
    api.fetchEnvironments.mockReturnValue(persisted.promise)
    api.fetchImpact.mockReturnValue(impact.promise)
    api.fetchTimeline.mockReturnValue(timeline.promise)

    await router.push('/impact/test')
    await router.isReady()
    const wrapper = mount(App, { global: { plugins: [router], stubs: { ChartCanvas: { template: '<canvas />' } } } })
    await flushPromises()

    expect(wrapper.find('.header-meta').exists()).toBe(true)
    expect(wrapper.find('.filter-bar').exists()).toBe(true)
    expect(wrapper.find('.filter-bar').element.closest('.main')).toBeNull()
    expect(wrapper.find('#dateRangePicker').exists()).toBe(true)
    expect(wrapper.find('#envTrigger').element.hasAttribute('disabled')).toBe(true)
    expect(wrapper.find('.chart-wrap.timeline .skeleton-block').exists()).toBe(true)
    expect(wrapper.find('.kpi-row .skeleton-block').exists()).toBe(true)
    expect(wrapper.find('.overview-row .skeleton-block').exists()).toBe(true)
    expect(wrapper.find('.metrics-row .skeleton-block').exists()).toBe(true)
    expect(wrapper.find('.table-card .skeleton-block').exists()).toBe(true)
    expect(api.fetchEnvironments).toHaveBeenCalledOnce()
    expect(api.fetchImpact).not.toHaveBeenCalled()
    expect(api.fetchTimeline).not.toHaveBeenCalled()

    const liveResult: LiveEnvironment[] = [{ id: 'test', name: 'Test environment', assets: [{ id: 'asset', name: 'Asset' }] }]
    live.resolve(liveResult)
    await flushPromises()
    expect(wrapper.find('#envTrigger').element.hasAttribute('disabled')).toBe(false)
    expect(api.fetchImpact).toHaveBeenCalledOnce()
    expect(api.fetchTimeline).toHaveBeenCalledOnce()

    const timelineResult: TimelineResponse = {
      stepMs: 86_400_000,
      stepLabel: 'day',
      stepPlural: 'days',
      bucketStarts: [1],
      series: [{ key: 'gwp', label: 'GWP', unit: 'kgCO2eq', buckets: [{ embedded: 1, use: 2 }] }],
    }
    timeline.resolve(timelineResult)
    await flushPromises()
    expect(wrapper.find('.chart-wrap.timeline .skeleton-block').exists()).toBe(false)
    expect(wrapper.find('.kpi-row .skeleton-block').exists()).toBe(true)

    persisted.resolve([{ id: 'test', name: 'Test environment', assets: [] }])
    impact.resolve([])
    await flushPromises()
    expect(wrapper.find('.kpi-row .skeleton-block').exists()).toBe(false)
    expect(wrapper.find('.overview-row').exists()).toBe(true)
    expect(wrapper.find('.metrics-row').exists()).toBe(true)
    expect(wrapper.find('.dashboard-counts').exists()).toBe(false)
    wrapper.unmount()
  })
})
