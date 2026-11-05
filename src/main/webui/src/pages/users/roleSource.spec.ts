import { describe, expect, it } from 'vitest'
import type { UserRole } from '@/domain/access'
import { roleSourceText } from '@/pages/users/roleSource'

describe('role source text', () => {
  it('describes provider and Wombat role sources in the tooltip', () => {
    const providerRole: UserRole = { role: 'USER', fromProvider: true, grant: null, removable: false }
    const grantedRole: UserRole = { role: 'ADMIN', fromProvider: false, grant: { grantedBy: 'Casey', grantedAt: '2025-02-03T04:05:00Z' }, removable: true }
    const dualRole: UserRole = { ...grantedRole, fromProvider: true }
    expect(roleSourceText(providerRole)).toBe('From the identity provider or auth.roles configuration')
    expect(roleSourceText(grantedRole)).toBe('Granted in Wombat by Casey on Feb 3, 2025 04:05 UTC')
    expect(roleSourceText(dualRole)).toBe('Granted in Wombat by Casey on Feb 3, 2025 04:05 UTC, also from the identity provider or configuration')
  })
})
