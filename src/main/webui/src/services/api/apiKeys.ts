import { request, type StatusMessages } from '@/services/api/client'

import type { ApiKeyInfo, ApiKeyRequest, ApiKeySettings } from '@/domain/access'
export type { ApiKeyInfo, ApiKeyRequest, ApiKeySettings } from '@/domain/access'

export const API_KEY_MESSAGES: StatusMessages = {
  400: 'The key was refused: check its name, permissions and expiry.',
  401: 'Your session has expired, reload the page to sign in again.',
  403: 'You are not allowed to do this.',
  404: 'This key no longer exists.',
}

/** The caller's own keys, or every user's with `all`, which needs `api.apikey.admin`. */
export function fetchApiKeys(all = false): Promise<ApiKeyInfo[]> {
  return request<ApiKeyInfo[]>(all ? '/api/keys?all=true' : '/api/keys', { messages: API_KEY_MESSAGES })
}

export function fetchApiKeySettings(): Promise<ApiKeySettings> {
  return request<ApiKeySettings>('/api/keys/settings', { messages: API_KEY_MESSAGES })
}

export function createApiKey(body: ApiKeyRequest): Promise<ApiKeyInfo> {
  return request<ApiKeyInfo>('/api/keys', { method: 'POST', body, messages: API_KEY_MESSAGES })
}

export function revokeApiKey(id: string): Promise<void> {
  return request<void>(`/api/keys/${encodeURIComponent(id)}`, { method: 'DELETE', messages: API_KEY_MESSAGES })
}
