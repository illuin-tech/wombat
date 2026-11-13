export type Permission =
  | 'ui.dashboard.read'
  | 'ui.apikeys.manage'
  | 'ui.users.read'
  | 'api.impact.read'
  | 'api.simulation.read'
  | 'api.environment.read'
  | 'api.environment.write'
  | 'api.metric.write'
  | 'api.apikey.manage'
  | 'api.apikey.admin'
  | 'api.user.manage'
  | 'api.user.read'

export interface ApiKeyInfo {
  id: string
  name: string
  permissions: string[]
  owner: string | null
  createdAt: string | null
  expiresAt: string | null
  revokedAt: string | null
  lastUsedAt: string | null
  token?: string
}

export interface ApiKeyRequest {
  name: string
  permissions: string[]
  expiresAt: string | null
}

export interface ApiKeySettings {
  grantablePermissions: string[]
  maxLifetimeDays: number
  allowNeverExpiring: boolean
}

export interface UserRole {
  role: string
  fromProvider: boolean
  grant: { grantedBy: string; grantedAt: string } | null
  removable: boolean
}

export interface UserInfo {
  id: string
  name: string
  email: string | null
  self: boolean
  createdAt: string | null
  lastSeenAt: string | null
  roles: UserRole[]
  grantableRoles: string[]
}
