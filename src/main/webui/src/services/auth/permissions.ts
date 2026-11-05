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

const MENU_LINKS: MenuLink[] = [
  { label: 'Users', to: '/users', permission: 'ui.users.read' },
  { label: 'API keys', to: '/api-keys', permission: 'ui.apikeys.manage' },
]

/** The admin links of the user menu the user may follow. */
export function menuLinks(user: PermissionHolder | null | undefined): MenuLink[] {
  return MENU_LINKS.filter(link => can(user, link.permission))
}
