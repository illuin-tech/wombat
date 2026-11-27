import { displayName, type AssetInfo } from '@/domain/environment'
import type { AssetEvaluationFailure } from '@/domain/impact'

/** An asset left out of the impact because its evaluation failed, as the dashboard lists it. */
export interface FailedAsset {
  id: string
  /** Its name when the environment's assets tell it, otherwise its id. */
  name: string
  environment: string
  reason: string
}

/**
 * Names each failed asset after the environment's `assets`, and its environment after `environments`; the first asset
 * of an id wins, as live assets come before database records.
 */
export function failedAssets(
  failures: readonly AssetEvaluationFailure[],
  assets: readonly AssetInfo[],
  environments: readonly { id: string; name?: string | null }[] = [],
): FailedAsset[] {
  const assetNames = new Map<string, string>()
  for (const asset of assets)
    if (asset.name && !assetNames.has(asset.id)) assetNames.set(asset.id, asset.name)
  const environmentNames = new Map(environments.map(environment => [environment.id, displayName(environment)]))
  return failures.map(({ assetIdentity, reason }) => ({
    id: assetIdentity.id,
    name: assetNames.get(assetIdentity.id) ?? assetIdentity.id,
    environment: environmentNames.get(assetIdentity.environmentId) ?? assetIdentity.environmentId,
    reason,
  }))
}
