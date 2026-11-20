import type { AssetInfo } from '@/domain/environment'

/** The record fields served next to an asset's own properties. */
const RECORD_FIELDS = new Set([
  'id', 'environmentId', 'name', 'type', 'uuid', 'owner', 'description',
  'provisioned', 'createdAt', 'updatedAt', 'disabledAt', 'deletedAt',
])

export type AssetProperties = Record<string, unknown>

/** Optional properties are served as null, which the server reads the same as an absent property. */
function withoutNulls(value: unknown): unknown {
  if (Array.isArray(value))
    return value.map(withoutNulls)
  if (value !== null && typeof value === 'object')
    return Object.fromEntries(Object.entries(value)
      .filter(([, item]) => item !== null && item !== undefined)
      .map(([key, item]) => [key, withoutNulls(item)]))
  return value
}

/** The type-specific properties of an asset, as its type's schema describes them. */
export function toAssetProperties(asset: AssetInfo): AssetProperties {
  const properties = Object.fromEntries(Object.entries(asset).filter(([key]) => !RECORD_FIELDS.has(key)))
  return withoutNulls(properties) as AssetProperties
}

function equal(a: unknown, b: unknown): boolean {
  if (a === b)
    return true
  if (Array.isArray(a) && Array.isArray(b))
    return a.length === b.length && a.every((item, index) => equal(item, b[index]))
  if (a !== null && b !== null && typeof a === 'object' && typeof b === 'object' && !Array.isArray(a) && !Array.isArray(b)) {
    const aKeys = Object.keys(a)
    const bKeys = Object.keys(b)
    return aKeys.length === bKeys.length && aKeys.every(key => equal((a as AssetProperties)[key], (b as AssetProperties)[key]))
  }
  return false
}

/** Whether both describe the same asset, a missing property being the same as a null one. */
export function isSameProperties(a: AssetProperties, b: AssetProperties): boolean {
  return equal(withoutNulls(a), withoutNulls(b))
}
