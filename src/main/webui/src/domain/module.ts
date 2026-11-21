import type { AssetType } from '@/domain/environment'

/** GET /api/modules: an asset type the server knows. */
export interface ModuleMetadata {
  type: AssetType
  /** Null when the serving instance does not detect the module. */
  assetClass: string | null
  /** Disabled modules accept assets, but their metrics are skipped. */
  enabled: boolean
  /** Simulated types only exist within simulation requests. A simulation holds the same types, and relies on it. */
  creatable: boolean
}

export type ModuleSource = 'core' | 'extension'

/** A module as Settings > Modules shows it: its registry state, and whether the running core holds it. */
export interface ModuleInfo extends ModuleMetadata {
  /** Whether the core running on the serving instance holds it. */
  deployed: boolean
  /** Whether the latest core deployment does not hold its registry state yet. */
  pending: boolean
  /** Whether no instance detects it any more, e.g. its extension jar was removed. */
  missing: boolean
  source: ModuleSource | null
  assetCount: number
}

export type CoreInstanceStatus = 'ok' | 'failed'

export interface CoreInstance {
  instanceId: string
  deployedVersion: number
  deployedId: string | null
  deployedAt: string
  /** Failed when the latest deployment did not build there: the instance keeps running its deployed version. */
  status: CoreInstanceStatus
  error: string | null
  detectedModules: string[]
  missingModules: string[]
  /** Whether it is the instance which served the request. */
  current: boolean
}

/** GET /api/modules/deployment */
export interface CoreDeploymentStatus {
  latest: { version: number; id: string; requestedAt: string; requestedBy: string | null; modules: string[] }
  instances: CoreInstance[]
  pending: { type: string; enabled: boolean }[]
  pollIntervalSeconds: number
}

/** POST /api/modules/deployment: the redeploy, and how it went on the instance which recorded it. */
export interface Redeployed {
  version: number
  id: string
  local: { status: CoreInstanceStatus; error: string | null; added: string[]; removed: string[] }
}

/** The first 7 characters of a deployment id, like a short commit id. */
export function shortDeploymentId(id: string): string {
  return id.slice(0, 7)
}

/** A JSON schema (draft 2020-12), as served by GET /api/modules/{id}/schema. */
export type JsonSchema = Record<string, unknown>
