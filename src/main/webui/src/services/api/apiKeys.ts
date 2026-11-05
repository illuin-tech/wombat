import { request, type StatusMessages } from '@/services/api/client'

import type { ApiKeyInfo, ApiKeyRequest, ApiKeySettings } from '@/domain/access'
export type { ApiKeyInfo, ApiKeyRequest, ApiKeySettings } from '@/domain/access'

/** Same texts as the former apiKeys.js. */
export const API_KEY_MESSAGES: StatusMessages = {
  400: 'The key was refused: check its name, permissions and expiry.',
  401: 'Your session has expired, reload the page to sign in again.',
  403: 'You are not allowed to manage API keys anymore.',
  404: 'This key no longer exists.',
}

export function fetchApiKeys(): Promise<ApiKeyInfo[]> {
  return request<ApiKeyInfo[]>('/api/keys', { messages: API_KEY_MESSAGES })
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
