import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { EnvironmentInfo, LiveEnvironment } from '@/domain/environment'
import type { ImpactResult, TimelineResponse } from '@/domain/impact'
import App from '@/App.vue'
import { router } from '@/router'
import { ApiError } from '@/services/api/client'
import { applyServices, resetServices } from '@/pages/impact/model/useDashboardQuery'

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
  NO_DATA_REASON: 'no-data',
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
    const impact = deferred<ImpactResult>()
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
    impact.resolve({ impacts: [], failures: [] })
    await flushPromises()
    expect(wrapper.find('.kpi-row .skeleton-block').exists()).toBe(false)
    expect(wrapper.find('.overview-row').exists()).toBe(true)
    expect(wrapper.find('.metrics-row').exists()).toBe(true)
    expect(wrapper.find('.dashboard-counts').exists()).toBe(false)
    wrapper.unmount()
  })
})

describe('ImpactPage errors', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  async function mountWithImpactError(error: Error) {
    api.fetchAppInfo.mockResolvedValue({
      auth: { enabled: false },
      user: { authenticated: false, name: null, roles: [], permissions: ['ui.dashboard.read'] },
      ui: { maxDateRange: { millis: 90 * 86_400_000, label: '90 days' } },
    })
    api.fetchLiveEnvironments.mockResolvedValue([{ id: 'test', name: 'Test environment', assets: [{ id: 'asset', name: 'Asset' }] }])
    api.fetchEnvironments.mockResolvedValue([])
    api.fetchImpact.mockRejectedValue(error)
    api.fetchTimeline.mockRejectedValue(error)

    await router.push('/impact/test')
    await router.isReady()
    const wrapper = mount(App, { global: { plugins: [router], stubs: { ChartCanvas: { template: '<canvas />' } } } })
    await flushPromises()
    return wrapper
  }

  it('tells an empty time range apart', async () => {
    const wrapper = await mountWithImpactError(new ApiError(400, 'Unexpected error (400), try again.', 'no-data'))
    expect(wrapper.find('.error-title').text()).toBe('No activity found')
    wrapper.unmount()
  })

  it('lists the assets which could not be evaluated when there is no data', async () => {
    const failures = [{ assetIdentity: { id: 'asset', environmentId: 'test', type: 'tech.illuin.wombat-module.kubernetes-api' }, reason: 'Cannot infer the server profile: nodes unreadable' }]
    const wrapper = await mountWithImpactError(new ApiError(400, 'Unexpected error (400), try again.', 'no-data', 'No assets matched scope', { reason: 'no-data', failures }))
    expect(wrapper.find('.error-title').text()).toBe('No activity found')
    expect(wrapper.find('.error-failures-title').text()).toBe('An asset could not be evaluated')
    expect(wrapper.find('.error-failures .unrecognized-asset-name').text()).toBe('Asset')
    expect(wrapper.find('.error-failures .failure-reason').text()).toBe('Cannot infer the server profile: nodes unreadable')
    expect(wrapper.find('.error-failures').text()).toContain('Test environment')
    wrapper.unmount()
  })

  it('shows any other failure with its message', async () => {
    const wrapper = await mountWithImpactError(new ApiError(500, 'Unexpected error (500), try again.'))
    expect(wrapper.find('.error-title').text()).toBe('The impact could not be loaded')
    expect(wrapper.find('.error-body').text()).toBe('Unexpected error (500), try again.')
    wrapper.unmount()
  })
})

describe('ImpactPage evaluation failures', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('opens the assets left out of the results from the recap', async () => {
    api.fetchAppInfo.mockResolvedValue({
      auth: { enabled: false },
      user: { authenticated: false, name: null, roles: [], permissions: ['ui.dashboard.read'] },
      ui: { maxDateRange: { millis: 90 * 86_400_000, label: '90 days' } },
    })
    api.fetchLiveEnvironments.mockResolvedValue([{ id: 'test', name: 'Test environment', assets: [{ id: 'asset', name: 'Asset' }, { id: 'cluster' }] }])
    api.fetchEnvironments.mockResolvedValue([])
    api.fetchImpact.mockResolvedValue({
      impacts: [],
      failures: [{ assetIdentity: { id: 'cluster', environmentId: 'test', type: 'tech.illuin.wombat-module.kubernetes-api' }, reason: 'Cannot infer the server profile' }],
    })
    api.fetchTimeline.mockResolvedValue({ stepMs: 86_400_000, stepLabel: 'day', stepPlural: 'days', bucketStarts: [], series: [] })

    await router.push('/impact/test')
    await router.isReady()
    const wrapper = mount(App, { global: { plugins: [router], stubs: { ChartCanvas: { template: '<canvas />' } } } })
    await flushPromises()

    const trigger = wrapper.find('#evaluationFailuresTrigger')
    expect(trigger.text()).toBe('1')
    await trigger.trigger('click')
    const item = document.body.querySelector('.asset-modal-overlay:not([hidden]) .unrecognized-asset-item')
    expect(item?.querySelector('.unrecognized-asset-name')?.textContent).toBe('cluster')
    expect(item?.querySelector('.failure-reason')?.textContent).toBe('Cannot infer the server profile')
    wrapper.unmount()
  })
})

describe('ImpactPage API requests', () => {
  afterEach(() => {
    document.body.innerHTML = ''
    resetServices()
  })

  it('builds separate requests from the selected filters and reloads them when they change', async () => {
    api.fetchAppInfo.mockReset().mockResolvedValue({
      auth: { enabled: false },
      user: { authenticated: false, name: null, roles: [], permissions: ['ui.dashboard.read'] },
      ui: { maxDateRange: { millis: 90 * 86_400_000, label: '90 days' } },
    })
    api.fetchLiveEnvironments.mockReset().mockResolvedValue([
      { id: 'test', name: 'Test environment', assets: [{ id: 'test-api', name: 'API' }, { id: 'test-worker', name: 'Worker' }] },
      { id: 'prod', name: 'Production', assets: [{ id: 'prod-api', name: 'API' }, { id: 'prod-db', name: 'Database' }] },
    ])
    api.fetchEnvironments.mockReset().mockResolvedValue([])
    api.fetchImpact.mockReset().mockResolvedValue({ impacts: [], failures: [] })
    api.fetchTimeline.mockReset().mockResolvedValue({
      stepMs: 86_400_000,
      stepLabel: 'day',
      stepPlural: 'days',
      bucketStarts: [Date.UTC(2026, 2, 1)],
      series: [{ key: 'gwp', label: 'GWP', unit: 'kgCO2eq', buckets: [{ embedded: 1, use: 2 }] }],
    })

    applyServices('test', ['test-api=cpu,queue', 'test-worker=database'])
    await router.push({ name: 'impact', params: { environment: 'test' }, query: { from: '2026-03-01T12:15', to: '2026-03-02T00:00' } })
    await router.isReady()
    const wrapper = mount(App, { global: { plugins: [router], stubs: { ChartCanvas: { template: '<canvas />' } } } })
    await flushPromises()

    const firstRange = { start: '2026-03-01T00:00:00Z', end: '2026-03-02T00:00:00Z' }
    expect(api.fetchImpact).toHaveBeenCalledWith({
      sourceTimeRange: firstRange,
      environments: [{ id: 'test', assets: [{ id: 'test-api' }, { id: 'test-worker' }] }],
    })
    expect(api.fetchTimeline).toHaveBeenCalledWith({
      sourceTimeRange: firstRange,
      environments: [{ id: 'test', assets: [
        { id: 'test-api', serviceIds: ['cpu', 'queue'] },
        { id: 'test-worker', serviceIds: ['database'] },
      ] }],
    })
    expect(wrapper.find('.chart-title').text()).toContain('Impact over time — day')
    expect(wrapper.find('.chart-wrap.timeline .skeleton-block').exists()).toBe(false)

    await router.push({ name: 'impact', params: { environment: 'prod' }, query: { from: '2026-04-04T00:00', to: '2026-04-05T00:00' } })
    await flushPromises()

    const updatedRange = { start: '2026-04-04T00:00:00Z', end: '2026-04-05T00:00:00Z' }
    expect(api.fetchImpact).toHaveBeenCalledTimes(2)
    expect(api.fetchImpact).toHaveBeenLastCalledWith({
      sourceTimeRange: updatedRange,
      environments: [{ id: 'prod', assets: [{ id: 'prod-api' }, { id: 'prod-db' }] }],
    })

    applyServices('prod', ['prod-db=postgres'])
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ from: '2026-04-04T00:00', to: '2026-04-05T00:00' })
    expect(api.fetchImpact).toHaveBeenCalledTimes(3)
    expect(api.fetchImpact).toHaveBeenLastCalledWith({
      sourceTimeRange: updatedRange,
      environments: [{ id: 'prod', assets: [{ id: 'prod-db' }] }],
    })
    expect(api.fetchTimeline).toHaveBeenCalledTimes(3)
    expect(api.fetchTimeline).toHaveBeenLastCalledWith({
      sourceTimeRange: updatedRange,
      environments: [{ id: 'prod', assets: [{ id: 'prod-db', serviceIds: ['postgres'] }] }],
    })
    expect(wrapper.find('.chart-title').text()).toContain('Impact over time — day')
    wrapper.unmount()
  })

  it('writes the default date range into the URL', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(Date.UTC(2026, 5, 10, 8, 30)) })
    api.fetchImpact.mockClear()
    try {
      await router.push({ name: 'impact', params: { environment: 'test' } })
      await router.isReady()
      const wrapper = mount(App, { global: { plugins: [router], stubs: { ChartCanvas: { template: '<canvas />' } } } })
      await flushPromises()

      expect(router.currentRoute.value.query).toEqual({ from: '2026-03-13T00:00', to: '2026-06-11T00:00' })
      expect(api.fetchImpact).toHaveBeenCalledTimes(1)
      expect(api.fetchImpact).toHaveBeenLastCalledWith({
        sourceTimeRange: { start: '2026-03-13T00:00:00Z', end: '2026-06-11T00:00:00Z' },
        environments: [{ id: 'test', assets: [{ id: 'test-api' }, { id: 'test-worker' }] }],
      })
      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })
})
