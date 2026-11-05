export type ServiceFamily = 'UNKNOWN' | 'KUBERNETES_CONTAINER' | 'LLM'
export type ActivityRegime = 'UNKNOWN' | 'MEASURED' | 'MODELED'

export interface AssetType {
  namespace: string
  localName: string
  name: string
  regime: ActivityRegime
  family: ServiceFamily
}

/** GET /api/environments: the asset properties are unwrapped next to `type`. */
export interface AssetInfo {
  id: string
  name: string
  'environment-id'?: string
  environmentId?: string
  type?: string | AssetType
  namespace?: string | null
  profile?: unknown
  deletedAt?: string | null
  [key: string]: unknown
}

export interface EnvironmentInfo {
  id: string
  uuid?: string
  name: string | null
  assets: AssetInfo[]
}

/** GET /api/impact/environments: the active runtime environment and its recognized assets. */
export interface LiveEnvironment {
  id: string
  name?: string | null
  assets: AssetInfo[]
}

/** Database assets outside the active runtime context, excluding soft-deleted records. */
export function findUnrecognizedAssets(databaseAssets: readonly AssetInfo[], liveAssets: readonly AssetInfo[]): AssetInfo[] {
  const liveIds = new Set(liveAssets.map(asset => asset.id))
  return databaseAssets.filter(asset => asset.deletedAt == null && !liveIds.has(asset.id))
}
