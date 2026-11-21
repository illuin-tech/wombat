import type { SimulationAssetInfo, SimulationInfo, SimulationPeriod, SimulationSummary } from '@/domain/simulation'
import { request, type StatusMessages } from '@/services/api/client'

export type { SimulationAssetInfo, SimulationInfo, SimulationSummary } from '@/domain/simulation'

export const SIMULATION_MESSAGES: StatusMessages = {
  400: 'The simulation was refused: check its id, name and description.',
  403: 'You are not allowed to edit simulations.',
  404: 'This simulation no longer exists.',
}

export const SIMULATION_ASSET_MESSAGES: StatusMessages = {
  400: 'The asset was refused: check its properties.',
  403: 'You are not allowed to edit simulations, or to read the copied asset.',
  404: 'This asset or its simulation no longer exists.',
}

/** The editable properties of a simulation: an update replaces all of them. */
export interface SimulationFields {
  name: string
  description: string | null
  notes: string | null
  referenceWindow: SimulationPeriod
}

export interface SimulationCreation {
  id: string
  name: string
  description: string | null
  /** The environment whose `assetIds` are copied. */
  parentEnvironmentId?: string | null
  assetIds?: string[]
}

/** The editable properties of a simulation asset: an update replaces all of them. */
export interface SimulationAssetFields {
  name: string
  description: string | null
  notes: string | null
  properties: Record<string, unknown>
}

/** Either a copy of `parentAssetId`, or a definition written from scratch with its `id`, `type` and `properties`. */
export interface SimulationAssetCreation {
  name: string
  description?: string | null
  notes?: string | null
  id?: string
  parentAssetId?: string
  type?: string
  properties?: Record<string, unknown>
}

const SANDBOX = '/api/simulation/sandbox'

function simulationPath(id: string): string {
  return `${SANDBOX}/${encodeURIComponent(id)}`
}

function assetPath(simulationId: string, assetId: string): string {
  return `${simulationPath(simulationId)}/assets/${encodeURIComponent(assetId)}`
}

export function fetchSimulations(): Promise<SimulationSummary[]> {
  return request<SimulationSummary[]>(SANDBOX, { messages: SIMULATION_MESSAGES })
}

export function fetchSimulation(id: string): Promise<SimulationInfo> {
  return request<SimulationInfo>(simulationPath(id), { messages: SIMULATION_MESSAGES })
}

export function createSimulation(body: SimulationCreation): Promise<SimulationInfo> {
  return request<SimulationInfo>(SANDBOX, {
    method: 'POST',
    body,
    messages: { ...SIMULATION_MESSAGES, 404: 'The environment or one of its assets no longer exists.', 409: 'A simulation with this id already exists.' },
  })
}

export function updateSimulation(id: string, body: SimulationFields): Promise<SimulationInfo> {
  return request<SimulationInfo>(simulationPath(id), { method: 'PUT', body, messages: SIMULATION_MESSAGES })
}

export function duplicateSimulation(id: string, body: { id: string; name: string }): Promise<SimulationInfo> {
  return request<SimulationInfo>(`${simulationPath(id)}/duplicate`, {
    method: 'POST',
    body,
    messages: { ...SIMULATION_MESSAGES, 409: 'A simulation with this id already exists.' },
  })
}

export function deleteSimulation(id: string): Promise<void> {
  return request<void>(simulationPath(id), { method: 'DELETE', messages: SIMULATION_MESSAGES })
}

/** The asset as it stands now, with how it compares to its parent. */
export function fetchSimulationAsset(simulationId: string, assetId: string): Promise<SimulationAssetInfo> {
  return request<SimulationAssetInfo>(assetPath(simulationId, assetId), { messages: SIMULATION_ASSET_MESSAGES })
}

export function createSimulationAsset(simulationId: string, body: SimulationAssetCreation): Promise<SimulationAssetInfo> {
  return request<SimulationAssetInfo>(`${simulationPath(simulationId)}/assets`, {
    method: 'POST',
    body,
    messages: { ...SIMULATION_ASSET_MESSAGES, 409: 'An asset with this id already exists in this simulation.' },
  })
}

export function updateSimulationAsset(simulationId: string, assetId: string, body: SimulationAssetFields): Promise<SimulationAssetInfo> {
  return request<SimulationAssetInfo>(assetPath(simulationId, assetId), { method: 'PUT', body, messages: SIMULATION_ASSET_MESSAGES })
}

export function deleteSimulationAsset(simulationId: string, assetId: string): Promise<void> {
  return request<void>(assetPath(simulationId, assetId), { method: 'DELETE', messages: SIMULATION_ASSET_MESSAGES })
}

/** Replaces the copy's properties with its parent's current ones, overwriting its changes. */
export function syncSimulationAsset(simulationId: string, assetId: string): Promise<SimulationAssetInfo> {
  return request<SimulationAssetInfo>(`${assetPath(simulationId, assetId)}/sync`, {
    method: 'POST',
    messages: { ...SIMULATION_ASSET_MESSAGES, 409: 'The original asset was deleted: there is nothing to sync from.' },
  })
}
