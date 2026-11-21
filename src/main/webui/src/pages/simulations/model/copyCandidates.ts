import { assetTypeName, byDisplayName, type AssetInfo } from '@/domain/environment'
import type { ModuleMetadata } from '@/domain/module'

/** An asset offered for copying into a simulation, and why it can't be when it can't. */
export interface CopyCandidate {
  asset: AssetInfo
  /** Null when the asset can be copied. */
  blocked: string | null
  /** Whether it is picked until the user says otherwise: inactive assets are not. */
  pickedByDefault: boolean
}

/* A simulation evaluates with the running modules, and a simulated type only serves stateless simulations */
function blockedReason(asset: AssetInfo, modules: ReadonlyMap<string, ModuleMetadata>): string | null {
  const module = modules.get(assetTypeName(asset) ?? '')
  if (!module)
    return 'Unknown type'
  if (!module.creatable)
    return 'Not for simulations'
  if (!module.enabled)
    return 'Module disabled'
  return null
}

/** The assets in their display order, each with whether it can be copied. */
export function copyCandidates(assets: readonly AssetInfo[], modules: readonly ModuleMetadata[]): CopyCandidate[] {
  const byType = new Map(modules.map(module => [module.type.name, module]))
  return [...assets].sort(byDisplayName).map(asset => {
    const blocked = blockedReason(asset, byType)
    return { asset, blocked, pickedByDefault: blocked === null && !asset.disabledAt }
  })
}
