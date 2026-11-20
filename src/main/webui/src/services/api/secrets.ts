import { request } from '@/services/api/client'

export interface SecretKeyCheck {
  key: string
  exists: boolean
}

/** Whether the key resolves through the server's secret resolvers; never discloses the value. */
export function checkSecretKey(key: string): Promise<SecretKeyCheck> {
  return request<SecretKeyCheck>(`/api/secret-keys/check?key=${encodeURIComponent(key)}`, {
    messages: { 400: 'Invalid secret name.', 403: 'You are not allowed to check secrets.' },
  })
}
