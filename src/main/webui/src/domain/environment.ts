export type ServiceFamily = 'UNKNOWN' | 'KUBERNETES_CONTAINER' | 'LLM'
export type ActivityRegime = 'UNKNOWN' | 'MEASURED' | 'MODELED'

export interface AssetType {
  namespace: string
  localName: string
  name: string
  regime: ActivityRegime
  family: ServiceFamily
}

/** GET /api/environments: the asset properties are unwrapped next to `type`, `name` is only known to database records. */
export interface AssetInfo {
  id: string
  name?: string
  environmentId?: string
  owner?: string | null
  type?: string | AssetType
  namespace?: string | null
  profile?: unknown
  uuid?: string
  description?: string | null
  provisioned?: boolean
  createdAt?: string | null
  updatedAt?: string | null
  disabledAt?: string | null
  deletedAt?: string | null
  [key: string]: unknown
}

export interface EnvironmentInfo {
  id: string
  uuid?: string
  name: string | null
  description?: string | null
  provisioned?: boolean
  createdAt?: string | null
  updatedAt?: string | null
  disabledAt?: string | null
  assets: AssetInfo[]
}

/** The asset's type name: the API serves it as a string, the live context as the full type. */
export function assetTypeName(asset: AssetInfo): string | null {
  if (typeof asset.type === 'string')
    return asset.type
  return asset.type?.name ?? null
}

/** The asset's family, from its type when served in full, otherwise from the type registry (`typesByName`). */
export function assetFamily(asset: AssetInfo, typesByName: ReadonlyMap<string, AssetType>): ServiceFamily {
  if (asset.type && typeof asset.type === 'object')
    return asset.type.family
  const name = assetTypeName(asset)
  return (name && typesByName.get(name)?.family) || 'UNKNOWN'
}

/** GET /api/impact/environments: the active runtime environment and its recognized assets. */
export interface LiveEnvironment {
  id: string
  name?: string | null
  assets: AssetInfo[]
}

/** Database assets outside the active runtime context, excluding soft-deleted and disabled records (left out on purpose). */
export function findUnrecognizedAssets(databaseAssets: readonly AssetInfo[], liveAssets: readonly AssetInfo[]): AssetInfo[] {
  const liveIds = new Set(liveAssets.map(asset => asset.id))
  return databaseAssets.filter(asset => asset.deletedAt == null && asset.disabledAt == null && !liveIds.has(asset.id))
}

/** The live assets with what only the database records know: their name, and the worker owning each one. */
export function withRecordDetails(liveAssets: readonly AssetInfo[], databaseAssets: readonly AssetInfo[]): AssetInfo[] {
  const records = new Map(databaseAssets.map(asset => [asset.id, asset]))
  return liveAssets.map(asset => {
    const record = records.get(asset.id)
    if (!record)
      return asset
    return {...asset, name: record.name ?? asset.name, ...(record.owner ? {owner: record.owner} : {})}
  })
}

/** The live environments with the names only their database records know. */
export function withEnvironmentNames(liveEnvironments: readonly LiveEnvironment[], records: readonly EnvironmentInfo[]): LiveEnvironment[] {
  const names = new Map(records.map(record => [record.id, record.name]))
  return liveEnvironments.map(environment => ({ ...environment, name: environment.name ?? names.get(environment.id) ?? null }))
}
