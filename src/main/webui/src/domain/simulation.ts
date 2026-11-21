import type { AssetInfo, AssetType } from '@/domain/environment'
import type { AssetImpact, Footprint } from '@/domain/impact'

/** The durations a simulation is projected over, and the reference windows it is evaluated on. */
export type SimulationPeriod = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR'

export const PERIODS: readonly SimulationPeriod[] = ['DAY', 'WEEK', 'MONTH', 'YEAR']

export const PERIOD_LABELS: Record<SimulationPeriod, string> = { DAY: 'Day', WEEK: 'Week', MONTH: 'Month', YEAR: 'Year' }

/** How a copied asset stands against the asset it was copied from. */
export type SourceStatus = 'in-sync' | 'diverged' | 'source-removed'

/** The asset a simulation asset was copied from. */
export interface AssetSource {
  assetId: string
  /** The source's environment, as it was at copy time. */
  environmentId: string
  status: SourceStatus
  /** When the definition was last taken from the source. */
  syncedAt: string
}

/** The fields of a simulation asset's record beyond those of an environment's asset. */
interface SimulationAssetRecord {
  simulationId: string
  notes?: string | null
  /** Absent for an asset written from scratch. */
  source?: AssetSource | null
}

/* A record, so that a field added to SimulationAssetRecord must be listed here */
const RECORD_FIELDS: Record<keyof SimulationAssetRecord, true> = { simulationId: true, notes: true, source: true }
/** What `toAssetProperties` leaves out of a simulation asset, beside the fields of any asset's record. */
export const SIMULATION_ASSET_RECORD_FIELDS: ReadonlySet<string> = new Set(Object.keys(RECORD_FIELDS))

/** An asset definition held by a simulation, its properties unwrapped as for an environment's asset. It belongs to no environment. */
export interface SimulationAssetInfo extends AssetInfo, SimulationAssetRecord {
  environmentId?: never
}

/** `environment/asset`, naming the source of a copy. */
export function sourceLabel(source: AssetSource): string {
  return `${source.environmentId}/${source.assetId}`
}

/** GET /api/simulation/sandbox: a simulation as listed, without its assets. */
export interface SimulationSummary {
  id: string
  name: string
  description?: string | null
  referenceWindow: SimulationPeriod
  sourceEnvironmentId?: string | null
  /** Null without a source environment, or once it was deleted. */
  sourceEnvironmentName?: string | null
  assetCount: number
  createdAt?: string | null
  updatedAt?: string | null
}

export interface SimulationInfo extends Omit<SimulationSummary, 'assetCount'> {
  notes?: string | null
  assets: SimulationAssetInfo[]
}

/** Whether the environment the simulation was generated from was deleted since. */
export function sourceEnvironmentRemoved(simulation: { sourceEnvironmentId?: string | null; sourceEnvironmentName?: string | null }): boolean {
  return !!simulation.sourceEnvironmentId && !simulation.sourceEnvironmentName
}

/** What an evaluation computes: the impact, the cost, or both. */
export type EvaluationScope = 'IMPACT' | 'COST'

/** Why an asset produced a result or not. */
export type EvaluationStatus = 'ok' | 'no-data' | 'module-disabled' | 'error'

/** An amount of money; `currency` is an ISO 4217 code. */
export interface Money {
  value: number
  currency: string
}

/** One service's footprint and cost, scaled to one period. */
export interface ScaledService {
  serviceId: string
  assetType: AssetType
  /** Null when the impact is not evaluated, or the service has none. */
  impact: Footprint | null
  cost: Money[] | null
}

/** The standardized part of results, scaled to one period by the server. Each is null when not evaluated. */
export interface ScaledResult {
  impact: Footprint | null
  /** One amount per currency, never converted; empty when nothing was priced. */
  cost: Money[] | null
  /** An asset's footprint per service; absent from the totals. */
  services?: ScaledService[] | null
}

/** An evaluation straight from the evaluator, as `/api/impact` serves it but for its asset's identity, or a cost. */
export type AssetEvaluation = (Omit<AssetImpact, 'assetIdentity'> & { type: 'IMPACT' }) | { type: 'COST'; [key: string]: unknown }

export interface AssetEvaluationResult {
  id: string
  status: EvaluationStatus
  message?: string | null
  /** The share of the window's slots with activity, from 0 to 1; null unless `ok`. */
  coverage?: number | null
  /** The evaluations over the window, unscaled: details for whoever wants them, never scaled here. */
  result: AssetEvaluation[]
  /** Empty unless `ok`. */
  scaled: Partial<Record<SimulationPeriod, ScaledResult>>
}

/** POST /api/simulation/sandbox/{id}/evaluation: the assets evaluated over the window, then scaled to every period. */
export interface SimulationEvaluation {
  window: { start: string; end: string; period: SimulationPeriod }
  /** How many windows each period holds, which the server scaled the results by. */
  factors: Record<SimulationPeriod, number>
  /** Summed over the assets evaluated `ok`. */
  totals: Record<SimulationPeriod, ScaledResult>
  assets: AssetEvaluationResult[]
}
