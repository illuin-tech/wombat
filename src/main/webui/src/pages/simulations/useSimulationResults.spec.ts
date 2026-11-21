import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, type EffectScope } from 'vue'
import type { SimulationEvaluation } from '@/domain/simulation'
import { evaluateSimulation } from '@/services/api/simulations'
import { useSimulationResults, type EvaluationTarget } from './useSimulationResults'

vi.mock('@/services/api/simulations', () => ({ evaluateSimulation: vi.fn() }))

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

const evaluated = (period: SimulationEvaluation['window']['period']): SimulationEvaluation =>
  ({ window: { start: '2026-11-20T00:00:00Z', end: '2026-11-21T00:00:00Z', period }, factors: {} as SimulationEvaluation['factors'], totals: {} as SimulationEvaluation['totals'], assets: [] })

describe('useSimulationResults', () => {
  let scope: EffectScope
  let target: EvaluationTarget

  beforeEach(() => {
    scope = effectScope()
    target = { id: 'what-if', scopes: ['IMPACT', 'COST'], referenceWindow: 'MONTH' }
    vi.useFakeTimers()
    vi.mocked(evaluateSimulation).mockReset()
  })
  afterEach(() => {
    scope.stop()
    vi.useRealTimers()
  })

  const setup = () => scope.run(() => useSimulationResults(() => target, 500))!

  it('recomputes once, a while after the last of several changes', async () => {
    vi.mocked(evaluateSimulation).mockResolvedValue(evaluated('WEEK'))
    const results = setup()

    results.schedule()
    await vi.advanceTimersByTimeAsync(300)
    target = { ...target, referenceWindow: 'WEEK' }
    results.schedule()
    expect(results.computing.value).toBe(true)
    await vi.advanceTimersByTimeAsync(499)
    expect(evaluateSimulation).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)
    expect(evaluateSimulation).toHaveBeenCalledExactlyOnceWith('what-if', { scopes: ['IMPACT', 'COST'], referenceWindow: 'WEEK' })
    expect(results.evaluation.value?.window.period).toBe('WEEK')
    expect(results.computing.value).toBe(false)
  })

  it('drops a response that arrives after a later request was sent', async () => {
    const first = deferred<SimulationEvaluation>()
    const second = deferred<SimulationEvaluation>()
    vi.mocked(evaluateSimulation).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const results = setup()

    void results.run()
    void results.run()
    second.resolve(evaluated('WEEK'))
    await vi.advanceTimersByTimeAsync(0)
    first.resolve(evaluated('DAY'))
    await vi.advanceTimersByTimeAsync(0)

    expect(results.evaluation.value?.window.period).toBe('WEEK')
    expect(results.computing.value).toBe(false)
  })

  it('keeps the last results through a failure, and runs at once over what was scheduled', async () => {
    vi.mocked(evaluateSimulation).mockResolvedValueOnce(evaluated('MONTH')).mockRejectedValueOnce(new Error('Boavizta is down'))
    const results = setup()

    await results.run()
    results.schedule()
    await results.run()
    await vi.advanceTimersByTimeAsync(1000)

    expect(evaluateSimulation).toHaveBeenCalledTimes(2)
    expect(results.evaluation.value?.window.period).toBe('MONTH')
    expect(results.error.value).toContain('Boavizta is down')
  })

  it('evaluates nothing without a scope', async () => {
    vi.mocked(evaluateSimulation).mockResolvedValue(evaluated('MONTH'))
    const results = setup()
    await results.run()

    target = { ...target, scopes: [] }
    await results.run()

    expect(evaluateSimulation).toHaveBeenCalledTimes(1)
    expect(results.evaluation.value).toBeNull()
  })
})
