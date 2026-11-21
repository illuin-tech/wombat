import type { AssetInfo } from '@/domain/environment'
import type { AssetImpact, Footprint } from '@/domain/impact'

/** The durations a simulation is projected over, and the reference windows it is evaluated on. */
export type SimulationPeriod = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR'

export const PERIODS: readonly SimulationPeriod[] = ['DAY', 'WEEK', 'MONTH', 'YEAR']

export const PERIOD_LABELS: Record<SimulationPeriod, string> = { DAY: 'Day', WEEK: 'Week', MONTH: 'Month', YEAR: 'Year' }

/** How a copied asset stands against the asset it was copied from. */
export type ParentStatus = 'in-sync' | 'diverged' | 'parent-removed'

/** An asset definition held by a simulation, its properties unwrapped as for an environment's asset. */
export interface SimulationAssetInfo extends AssetInfo {
  simulationId: string
  notes?: string | null
  parentAssetId?: string | null
  parentEnvironmentId?: string | null
  /** Absent without a parent. */
  parentStatus?: ParentStatus | null
  syncedAt?: string | null
}

/** GET /api/simulation/sandbox: a simulation as listed, without its assets. */
export interface SimulationSummary {
  id: string
  name: string
  description?: string | null
  referenceWindow: SimulationPeriod
  parentEnvironmentId?: string | null
  /** Null without a parent environment, or once it was deleted. */
  parentEnvironmentName?: string | null
  assetCount: number
  createdAt?: string | null
  updatedAt?: string | null
}

export interface SimulationInfo extends Omit<SimulationSummary, 'assetCount'> {
  notes?: string | null
  assets: SimulationAssetInfo[]
}

/** Whether the environment the simulation was generated from was deleted since. */
export function parentEnvironmentRemoved(simulation: { parentEnvironmentId?: string | null; parentEnvironmentName?: string | null }): boolean {
  return !!simulation.parentEnvironmentId && !simulation.parentEnvironmentName
}

/** What an evaluation computes: the impact, the cost, or both. */
export type EvaluationScope = 'IMPACT' | 'COST'

/** Why an asset produced a result or not. */
export type EvaluationStatus = 'ok' | 'no-data' | 'module-disabled' | 'error'

/** The standardized part of a result, scaled to one period. Each is null when not evaluated. */
export interface ScaledResult {
  impact: Footprint | null
  /** Always null for now: a cost carries no amount yet. */
  cost: { value: number; unit: string } | null
}

/** An evaluation straight from the evaluator: an impact as `/api/impact` serves it, or a cost. */
export type AssetEvaluation = (AssetImpact & { type: 'IMPACT' }) | { type: 'COST'; [key: string]: unknown }

export interface AssetEvaluationResult {
  id: string
  status: EvaluationStatus
  message?: string | null
  /** The share of the window's slots with activity, from 0 to 1; null unless `ok`. */
  coverage?: number | null
  /** The evaluations over the window, unscaled. */
  result: AssetEvaluation[]
  /** Empty unless `ok`. */
  scaled: Partial<Record<SimulationPeriod, ScaledResult>>
}

/** POST /api/simulation/sandbox/{id}/evaluation: the assets evaluated over the window, then scaled to every period. */
export interface SimulationEvaluation {
  window: { start: string; end: string; period: SimulationPeriod }
  /** How many windows each period holds: a period's results are the window's times its factor. */
  factors: Record<SimulationPeriod, number>
  /** Summed over the assets evaluated `ok`. */
  totals: Record<SimulationPeriod, ScaledResult>
  assets: AssetEvaluationResult[]
}
