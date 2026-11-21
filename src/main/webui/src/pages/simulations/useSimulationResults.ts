import { onScopeDispose, readonly, ref, shallowRef, type Ref } from 'vue'
import type { EvaluationScope, SimulationEvaluation, SimulationPeriod } from '@/domain/simulation'
import { errorText } from '@/services/api/client'
import { evaluateSimulation } from '@/services/api/simulations'

/** Idleness after the last change before recomputing, in milliseconds. */
export const RECOMPUTE_DELAY = 500

export interface EvaluationTarget {
  id: string
  scopes: EvaluationScope[]
  referenceWindow: SimulationPeriod
}

export interface SimulationResults {
  evaluation: Readonly<Ref<SimulationEvaluation | null>>
  error: Readonly<Ref<string | null>>
  /** Whether a recompute is due or running: the last results stay shown meanwhile. */
  computing: Readonly<Ref<boolean>>
  /** Recomputes right away, dropping what was scheduled. */
  run: () => Promise<void>
  /** Recomputes a while after the last call. */
  schedule: () => void
}

/**
 * Evaluates the simulation `target` names, keeping only the latest request's outcome: a response that arrives after a
 * later request was sent is dropped. A target with no scope evaluates nothing.
 */
export function useSimulationResults(target: () => EvaluationTarget | null, delay = RECOMPUTE_DELAY): SimulationResults {
  const evaluation = shallowRef<SimulationEvaluation | null>(null)
  const error = ref<string | null>(null)
  const computing = ref(false)
  let sequence = 0
  let timer: ReturnType<typeof setTimeout> | undefined

  async function run(): Promise<void> {
    clearTimeout(timer)
    const current = ++sequence
    const requested = target()
    if (!requested || requested.scopes.length === 0) {
      evaluation.value = null
      error.value = null
      computing.value = false
      return
    }
    computing.value = true
    try {
      const result = await evaluateSimulation(requested.id, { scopes: requested.scopes, referenceWindow: requested.referenceWindow })
      if (current !== sequence) return
      evaluation.value = result
      error.value = null
    } catch (caught) {
      if (current === sequence) error.value = errorText(caught)
    } finally {
      if (current === sequence) computing.value = false
    }
  }

  onScopeDispose(() => clearTimeout(timer))

  return {
    evaluation,
    error: readonly(error),
    computing: readonly(computing),
    run,
    schedule() {
      clearTimeout(timer)
      computing.value = true
      timer = setTimeout(() => void run(), delay)
    },
  }
}
