export type ServiceFamily = 'UNKNOWN' | 'KUBERNETES_CONTAINER' | 'LLM'
export type ActivityRegime = 'UNKNOWN' | 'MEASURED' | 'MODELED'

export interface AssetType {
  namespace: string
  localName: string
  name: string
  regime: ActivityRegime
  family: ServiceFamily
}

/** The fields of an asset's record, as opposed to the type-specific properties served next to them. */
export interface AssetRecord {
  id: string
  name?: string
  environmentId?: string
  owner?: string | null
  type?: string | AssetType
  uuid?: string
  description?: string | null
  provisioned?: boolean
  createdAt?: string | null
  updatedAt?: string | null
  disabledAt?: string | null
  deletedAt?: string | null
}

/* A record, so that a field added to AssetRecord must be listed here */
const RECORD_FIELDS: Record<keyof AssetRecord, true> = {
  id: true, name: true, environmentId: true, owner: true, type: true, uuid: true, description: true,
  provisioned: true, createdAt: true, updatedAt: true, disabledAt: true, deletedAt: true,
}
export const ASSET_RECORD_FIELDS: ReadonlySet<string> = new Set(Object.keys(RECORD_FIELDS))

/** GET /api/environments: the asset properties are unwrapped next to `type`, `name` is only known to database records. */
export interface AssetInfo extends AssetRecord {
  namespace?: string | null
  profile?: unknown
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

/** What an environment or an asset is called: its name, or its id when it has none. */
export function displayName(named: { id: string; name?: string | null }): string {
  return named.name ?? named.id
}

/** Sorts environments or assets by what they are called. */
export function byDisplayName(a: { id: string; name?: string | null }, b: { id: string; name?: string | null }): number {
  return displayName(a).localeCompare(displayName(b))
}

/** The asset's type name: the API serves it as a string, the live context as the full type. */
export function assetTypeName(asset: AssetInfo): string | null {
  if (typeof asset.type === 'string')
    return asset.type
  return asset.type?.name ?? null
}

/** Local name of the module type whose assets may leave their server profile to infer from their cluster. */
const KUBERNETES_API_TYPE = 'kubernetes-api'

/** Whether the asset's hardware is inferred from its cluster: a `kubernetes-api` asset whose profile is missing or of type `inferred`. */
export function hasInferredProfile(asset: AssetInfo): boolean {
  const name = assetTypeName(asset)
  if (!name || !(name === KUBERNETES_API_TYPE || name.endsWith(`.${KUBERNETES_API_TYPE}`)))
    return false
  const profile = asset.profile
  if (profile === undefined || profile === null)
    return true
  return typeof profile === 'object' && (profile as Record<string, unknown>).type === 'inferred'
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
