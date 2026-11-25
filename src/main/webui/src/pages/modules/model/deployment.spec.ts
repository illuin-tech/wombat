import { describe, expect, it, vi } from 'vitest'
import type { CoreDeploymentStatus, CoreInstance } from '@/domain/module'
import { awaitConvergence, convergence, convergenceTimeoutMs, divergentInstances } from './deployment'

function instance(instanceId: string, deployedVersion: number, overrides: Partial<CoreInstance> = {}): CoreInstance {
  return {
    instanceId, deployedVersion, deployedId: `id-${deployedVersion}`, deployedAt: '2026-11-20T14:02:00Z',
    status: 'ok', error: null, detectedModules: ['a', 'b'], missingModules: [], current: false,
    ...overrides,
  }
}

function status(...instances: CoreInstance[]): CoreDeploymentStatus {
  return {
    latest: { version: 7, id: 'id-7', requestedAt: '2026-11-20T14:02:00Z', requestedBy: null, modules: [] },
    instances, pending: [], orderPending: false, pollIntervalSeconds: 30,
  }
}

describe('convergence', () => {
  it('is done once every instance runs the version', () => {
    expect(convergence(status(instance('a', 7), instance('b', 8)), 7)).toMatchObject({ state: 'done', converged: 2, total: 2 })
  })

  it('waits for the instances still behind', () => {
    expect(convergence(status(instance('a', 7), instance('b', 6)), 7)).toMatchObject({ state: 'waiting', converged: 1, total: 2 })
  })

  it('fails when an instance could not build the version', () => {
    const broken = instance('b', 6, { status: 'failed', error: 'broken module' })
    expect(convergence(status(instance('a', 7), broken), 7)).toMatchObject({ state: 'failed', failed: [broken] })
  })
})

describe('awaitConvergence', () => {
  it('polls until every instance runs the version', async () => {
    const fetchStatus = vi.fn<() => Promise<CoreDeploymentStatus>>()
      .mockResolvedValueOnce(status(instance('a', 7), instance('b', 6)))
      .mockResolvedValueOnce(status(instance('a', 7), instance('b', 7)))
    const progress = vi.fn()

    const result = await awaitConvergence(fetchStatus, 7, { sleep: async () => {}, onProgress: progress })

    expect(result.state).toBe('done')
    expect(fetchStatus).toHaveBeenCalledTimes(2)
    expect(progress).toHaveBeenNthCalledWith(1, expect.objectContaining({ converged: 1, total: 2 }))
  })

  it('gives up past the timeout', async () => {
    let clock = 0
    const fetchStatus = vi.fn(async () => status(instance('a', 6)))

    const result = await awaitConvergence(fetchStatus, 7, {
      now: () => clock,
      sleep: async ms => { clock += ms },
      pollMs: 10_000,
    })

    expect(result.state).toBe('timeout')
    expect(clock).toBeGreaterThanOrEqual(convergenceTimeoutMs(30))
  })

  it('stops polling once aborted', async () => {
    const controller = new AbortController()
    const fetchStatus = vi.fn(async () => status(instance('a', 6)))

    const result = await awaitConvergence(fetchStatus, 7, { sleep: async () => controller.abort(), signal: controller.signal })

    expect(result.state).toBe('waiting')
    expect(fetchStatus).toHaveBeenCalledTimes(2)
  })
})

describe('divergentInstances', () => {
  it('lists the instances detecting other modules than the current one', () => {
    const other = instance('b', 7, { detectedModules: ['a'] })
    expect(divergentInstances([instance('a', 7, { current: true }), instance('c', 7, { detectedModules: ['b', 'a'] }), other])).toEqual([other])
  })
})
