import type { AssetInfo } from '@/domain/environment'
import { request, type StatusMessages } from '@/services/api/client'

export type { AssetInfo } from '@/domain/environment'

export const ASSET_MESSAGES: StatusMessages = {
  400: 'The asset was refused: check its properties.',
  403: 'You are not allowed to edit assets.',
  404: 'This asset or its environment no longer exists.',
  409: 'This asset cannot be changed.',
}

/** The editable properties of an asset: an update replaces all of them. */
export interface AssetFields {
  name: string
  description: string | null
  disabled: boolean
  /** The type-specific properties, described by the type's JSON schema. */
  properties: Record<string, unknown>
}

export interface AssetCreation extends AssetFields {
  id: string
  environmentId: string
  type: string
}

function assetPath(id: string): string {
  return `/api/assets/${encodeURIComponent(id)}`
}

export function fetchAssets(environmentId: string): Promise<AssetInfo[]> {
  return request<AssetInfo[]>(`/api/assets?environmentId=${encodeURIComponent(environmentId)}`, { messages: ASSET_MESSAGES })
}

export function createAsset(body: AssetCreation): Promise<AssetInfo> {
  return request<AssetInfo>('/api/assets', {
    method: 'POST',
    body,
    messages: { ...ASSET_MESSAGES, 409: 'An asset with this id already exists.' },
  })
}

export function updateAsset(id: string, body: AssetFields): Promise<AssetInfo> {
  return request<AssetInfo>(assetPath(id), {
    method: 'PUT',
    body,
    messages: { ...ASSET_MESSAGES, 409: 'This asset is provisioned: it cannot be edited.' },
  })
}

export function deleteAsset(id: string): Promise<void> {
  return request<void>(assetPath(id), {
    method: 'DELETE',
    messages: { ...ASSET_MESSAGES, 409: 'This asset is provisioned: it cannot be deleted.' },
  })
}
