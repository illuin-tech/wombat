import type { Permission } from '@/domain/access'
export type { Permission } from '@/domain/access'

export interface PermissionHolder {
  permissions: readonly string[]
}

export function can(user: PermissionHolder | null | undefined, permission: Permission): boolean {
  return !!user && user.permissions.includes(permission)
}

export interface MenuLink {
  label: 'Users' | 'API keys' | 'Modules'
  to: string
  permission: Permission
  icon: 'key' | 'users' | 'modules'
}

const SETTINGS_LINKS: MenuLink[] = [
  { label: 'Modules', to: '/modules', permission: 'ui.modules.read', icon: 'modules' },
  { label: 'API keys', to: '/api-keys', permission: 'ui.apikeys.manage', icon: 'key' },
  { label: 'Users', to: '/users', permission: 'ui.users.read', icon: 'users' },
]

/** The links of the header Settings menu the user may follow. */
export function settingsLinks(user: PermissionHolder | null | undefined): MenuLink[] {
  return SETTINGS_LINKS.filter(link => can(user, link.permission))
}
