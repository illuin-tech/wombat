import type { Permission } from '@/domain/access'
export type { Permission } from '@/domain/access'

export interface PermissionHolder {
  permissions: readonly string[]
}

export function can(user: PermissionHolder | null | undefined, permission: Permission): boolean {
  return !!user && user.permissions.includes(permission)
}

export interface MenuLink {
  label: 'Users' | 'API keys'
  to: string
  permission: Permission
}

const SETTINGS_LINKS: MenuLink[] = [
  { label: 'API keys', to: '/api-keys', permission: 'ui.apikeys.manage' },
  { label: 'Users', to: '/users', permission: 'ui.users.read' },
]

/** The links of the header Settings menu the user may follow. */
export function settingsLinks(user: PermissionHolder | null | undefined): MenuLink[] {
  return SETTINGS_LINKS.filter(link => can(user, link.permission))
}
