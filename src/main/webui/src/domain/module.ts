import type { AssetType } from '@/domain/environment'

/** GET /api/modules: an asset type the server knows. */
export interface ModuleMetadata {
  type: AssetType
  assetClass: string
  /** Disabled modules accept assets, but their metrics are skipped. */
  enabled: boolean
  /** Simulated types only exist within simulation requests. */
  creatable: boolean
}

/** A JSON schema (draft 2020-12), as served by GET /api/modules/{id}/schema. */
export type JsonSchema = Record<string, unknown>
