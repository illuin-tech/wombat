import { request, type StatusMessages } from '@/services/api/client'

import type { UserInfo, UserRole } from '@/domain/access'
export type { UserInfo, UserRole } from '@/domain/access'

/** Same texts as the former users.js. */
export const USER_ROLE_MESSAGES: StatusMessages = {
  400: 'Unknown role.',
  401: 'Your session has expired, reload the page to sign in again.',
  403: 'You are not allowed to manage users anymore.',
  404: 'This user no longer exists.',
  409: 'You cannot remove your own admin role: ask another admin.',
}

export function fetchUsers(): Promise<UserInfo[]> {
  return request<UserInfo[]>('/api/users', { messages: USER_ROLE_MESSAGES })
}

function rolePath(userId: string, role: string): string {
  return `/api/users/${encodeURIComponent(userId)}/roles/${encodeURIComponent(role)}`
}

export function grantRole(userId: string, role: string): Promise<void> {
  return request<void>(rolePath(userId, role), { method: 'PUT', messages: USER_ROLE_MESSAGES })
}

export function removeRole(userId: string, role: string): Promise<void> {
  return request<void>(rolePath(userId, role), { method: 'DELETE', messages: USER_ROLE_MESSAGES })
}
