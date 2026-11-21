import type { AssetInfo } from '@/domain/environment'

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
