import { effectScope } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import type { EnvironmentInfo, LiveEnvironment } from '@/domain/environment'
import type { AssetImpact, ImpactRequest, TimelineResponse } from '@/domain/impact'
import { useDashboardData, type DashboardApi } from './useDashboardData'

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

const requests = {
  impact: { environments: [] } as ImpactRequest,
  timeline: { environments: [] } as ImpactRequest,
  assetIds: [],
}

describe('useDashboardData', () => {
  it('loads persisted environments independently and starts impact and timeline together', async () => {
    const live = deferred<LiveEnvironment[]>()
    const persisted = deferred<EnvironmentInfo[]>()
    const impact = deferred<AssetImpact[]>()
    const timeline = deferred<TimelineResponse>()
    const api: DashboardApi = {
      fetchLiveEnvironments: vi.fn(() => live.promise),
      fetchEnvironments: vi.fn(() => persisted.promise),
      fetchImpact: vi.fn(() => impact.promise),
      fetchTimeline: vi.fn(() => timeline.promise),
    }
    const scope = effectScope()
    const data = scope.run(() => useDashboardData(api))!

    data.loadEnvironments()
    expect(data.liveEnvironments.loading.value).toBe(true)
    expect(data.persistedEnvironments.loading.value).toBe(true)

    const saved: EnvironmentInfo[] = [{ id: 'test', name: 'Test', assets: [] }]
    persisted.resolve(saved)
    await Promise.resolve()
    await Promise.resolve()
    expect(data.persistedEnvironments.data.value).toEqual(saved)
    expect(data.liveEnvironments.data.value).toBeNull()

    const active: LiveEnvironment[] = [{ id: 'test', assets: [] }]
    live.resolve(active)
    await Promise.resolve()
    await Promise.resolve()
    expect(data.liveEnvironments.data.value).toEqual(active)

    data.loadEvaluation('test', requests)
    expect(api.fetchImpact).toHaveBeenCalledWith(requests.impact)
    expect(api.fetchTimeline).toHaveBeenCalledWith(requests.timeline)
    expect(data.impact.loading.value).toBe(true)
    expect(data.timeline.loading.value).toBe(true)

    const timelineResult: TimelineResponse = { stepMs: 1, stepLabel: 'step', stepPlural: 'steps', bucketStarts: [1], series: [] }
    timeline.resolve(timelineResult)
    await Promise.resolve()
    await Promise.resolve()
    expect(data.timeline.data.value).toEqual(timelineResult)
    expect(data.impact.data.value).toBeNull()

    impact.resolve([])
    await Promise.resolve()
    await Promise.resolve()
    expect(data.impact.data.value).toEqual([])
    expect(data.impactEnvironmentId.value).toBe('test')
    expect(data.timelineEnvironmentId.value).toBe('test')
    scope.stop()
  })

  it('does not associate stale evaluation results with a newer environment', async () => {
    const first = deferred<AssetImpact[]>()
    const second = deferred<AssetImpact[]>()
    let impactCall = 0
    const api: DashboardApi = {
      fetchLiveEnvironments: vi.fn(async () => []),
      fetchEnvironments: vi.fn(async () => []),
      fetchImpact: vi.fn(() => (++impactCall === 1 ? first.promise : second.promise)),
      fetchTimeline: vi.fn(async () => ({ stepMs: 1, stepLabel: 'step', stepPlural: 'steps', bucketStarts: [], series: [] })),
    }
    const scope = effectScope()
    const data = scope.run(() => useDashboardData(api))!

    data.loadEvaluation('first', requests)
    data.loadEvaluation('second', requests)
    first.resolve([])
    await Promise.resolve()
    await Promise.resolve()
    expect(data.impactEnvironmentId.value).toBeNull()
    second.resolve([])
    await Promise.resolve()
    await Promise.resolve()
    expect(data.impactEnvironmentId.value).toBe('second')
    scope.stop()
  })
})
