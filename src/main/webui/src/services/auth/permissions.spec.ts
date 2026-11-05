import { describe, expect, it } from 'vitest'
import type { RouteLocationNormalized } from 'vue-router'
import { permissionGuard } from '@/services/auth/guard'
import { menuLinks } from '@/services/auth/permissions'

function route(meta: RouteLocationNormalized['meta'], fullPath = '/users'): RouteLocationNormalized {
  return { meta, fullPath } as RouteLocationNormalized
}

async function guard(permissions: string[] | null, to: RouteLocationNormalized) {
  const check = permissionGuard(async () => (permissions === null ? null : { permissions }))
  return (check as (to: RouteLocationNormalized) => Promise<unknown>)(to)
}

describe('permission guard', () => {
  it('lets public routes and routes without permission through', async () => {
    expect(await guard(null, route({ public: true }))).toBe(true)
    expect(await guard(null, route({}))).toBe(true)
  })

  it('allows a route whose permission the user holds', async () => {
    expect(await guard(['ui.users.read'], route({ permission: 'ui.users.read' }))).toBe(true)
  })

  it('sends to the forbidden view when the permission is missing', async () => {
    expect(await guard(['ui.dashboard.read'], route({ permission: 'ui.users.read' }))).toEqual({
      name: 'forbidden', query: { from: '/users' }, replace: true,
    })
  })
})

describe('user menu links', () => {
  it('shows the admin links only with their permissions', () => {
    expect(menuLinks({ permissions: ['ui.dashboard.read'] })).toEqual([])
    expect(menuLinks({ permissions: ['ui.users.read'] }).map(link => link.label)).toEqual(['Users'])
    expect(menuLinks({ permissions: ['ui.users.read', 'ui.apikeys.manage'] }).map(link => link.to)).toEqual(['/users', '/api-keys'])
    expect(menuLinks(null)).toEqual([])
  })
})
