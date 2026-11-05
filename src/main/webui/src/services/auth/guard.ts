import type { NavigationGuard, RouteLocationNormalized } from 'vue-router'
import { can, type PermissionHolder } from '@/services/auth/permissions'

/**
 * Client-side mirror of the server's page permissions: the server already refuses the page, this only
 * avoids navigating within the SPA to a view whose data the user cannot load.
 */
export function permissionGuard(currentUser: () => Promise<PermissionHolder | null>): NavigationGuard {
  return async (to: RouteLocationNormalized) => {
    const permission = to.meta.permission
    if (to.meta.public || !permission)
      return true

    const user = await currentUser().catch(() => null)
    if (can(user, permission))
      return true
    return { name: 'forbidden', query: { from: to.fullPath }, replace: true }
  }
}
