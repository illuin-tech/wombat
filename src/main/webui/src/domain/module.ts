import type { AssetType, ServiceFamily } from '@/domain/environment'

/** The type of a module serving every asset of its family, e.g. an impact methodology: it defines no asset. */
export interface FamilyType {
  namespace: string
  localName: string
  name: string
  family: ServiceFamily
}

interface ModuleState {
  /** Null when the serving instance does not detect the module, and for a family module. */
  assetClass: string | null
  /** Disabled modules accept assets, but their metrics are skipped. */
  enabled: boolean
  /** Simulated types only exist within simulation requests. A simulation holds the same types, and relies on it. */
  creatable: boolean
}

/** GET /api/modules: a module the server knows, which defines an asset type or serves a whole family. */
export type ModuleMetadata =
  | (ModuleState & { kind: 'asset'; type: AssetType })
  | (ModuleState & { kind: 'family'; type: FamilyType })

export type AssetModuleMetadata = Extract<ModuleMetadata, { kind: 'asset' }>

export function isAssetModule(module: ModuleMetadata): module is AssetModuleMetadata {
  return module.kind === 'asset'
}

/** The asset types of the modules defining one, by name: family modules define none. */
export function assetTypesByName(modules: ModuleMetadata[]): Map<string, AssetType> {
  return new Map(modules.filter(isAssetModule).map(module => [module.type.name, module.type]))
}

export type ModuleSource = 'core' | 'extension'

/** What a module provides for its assets, or those of its family: two family modules only compete for what both provide. */
export type ModuleFeature = 'source' | 'activity' | 'impact' | 'cost'

/** A module as Settings > Modules shows it: its registry state, and whether the running core holds it. */
export type ModuleInfo = ModuleMetadata & {
  /** Whether the core running on the serving instance holds it. */
  deployed: boolean
  /** Whether the latest core deployment does not hold its registry state yet. */
  pending: boolean
  /** Whether no instance detects it any more, e.g. its extension jar was removed. */
  missing: boolean
  source: ModuleSource | null
  /** Its place in the order of the modules, from 1: the family modules of a family are tried in that order. Null until placed. */
  ordering: number | null
  /** Empty when the serving instance does not detect the module. */
  provides: ModuleFeature[]
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
  /** The modules the latest deployment holds at another place than their order: the fewest which, moved, give it. */
  moved: string[]
  pollIntervalSeconds: number
}

/** GET /api/modules/precedence: which family module serves first, for each family and component. */
export interface ModulePrecedence {
  families: FamilyPrecedence[]
  /** Whether the next deployment changes any of them. */
  changed: boolean
}

export interface FamilyPrecedence {
  family: ServiceFamily
  /** The components some family module of the family provides, in either deployment. */
  features: FeaturePrecedence[]
}

/** The family modules providing a component, the one serving first first: the next ones serve the assets it does not accept. */
export interface FeaturePrecedence {
  feature: ModuleFeature
  /** In the latest deployment. */
  deployed: string[]
  /** In the next one: the enabled modules, in their order. */
  next: string[]
  changed: boolean
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
