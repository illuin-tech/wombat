import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import type { UserInfo } from '@/domain/access'
import UserTable from './UserTable.vue'

const user: UserInfo = {
  id: 'user-1',
  name: 'Jean-Michel Laurent',
  email: 'jean-michel@example.test',
  self: false,
  createdAt: '2026-01-01T00:00:00Z',
  lastSeenAt: '2026-02-01T00:00:00Z',
  roles: [
    {
      role: 'admin',
      fromProvider: false,
      grant: { grantedBy: 'owner', grantedAt: '2026-01-02T00:00:00Z' },
      removable: true,
    },
  ],
  grantableRoles: ['operator'],
}

describe('UserTable loading state', () => {
  it('keeps the table shell and renders placeholder rows while users load', () => {
    const wrapper = mount(UserTable, { props: { users: [], canManage: true, loading: true } })

    expect(wrapper.find('.table-card').exists()).toBe(true)
    expect(wrapper.findAll('tbody tr')).toHaveLength(5)
    expect(wrapper.findAll('tbody tr')[0].findAll('td')).toHaveLength(6)
    expect(wrapper.find('.apikey-empty').exists()).toBe(false)
    wrapper.unmount()
  })
})

describe('UserTable role interactions', () => {
  it('emits the user and role when granting or removing a role', async () => {
    const wrapper = mount(UserTable, { props: { users: [user], canManage: true } })

    await wrapper.get('.user-grant').trigger('click')
    expect(wrapper.emitted('grant')).toEqual([[user, 'operator']])

    await wrapper.get('[aria-label="Remove role admin from Jean-Michel Laurent"]').trigger('click')
    expect(wrapper.emitted('remove')).toEqual([[user, 'admin']])
    wrapper.unmount()
  })

  it('hides grant controls when the viewer cannot manage users', () => {
    const wrapper = mount(UserTable, { props: { users: [user], canManage: false } })

    expect(wrapper.findAll('thead th').map((header) => header.text())).not.toContain('Grant')
    expect(wrapper.find('.user-grant').exists()).toBe(false)
    wrapper.unmount()
  })

  it('disables grant and role-removal controls while an action is pending', () => {
    const wrapper = mount(UserTable, { props: { users: [user], canManage: true, pending: true } })

    expect((wrapper.get('.user-grant').element as HTMLButtonElement).disabled).toBe(true)
    expect((wrapper.get('.user-role-remove').element as HTMLButtonElement).disabled).toBe(true)
    wrapper.unmount()
  })
})
