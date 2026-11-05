import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import type { ApiKeyInfo } from '@/domain/access'
import ApiKeyTable from './ApiKeyTable.vue'

const activeKey: ApiKeyInfo = {
  id: 'active',
  name: 'My pipeline',
  permissions: ['api.impact.read'],
  owner: 'Alice',
  'created-at': '2026-01-01T00:00:00Z',
  'expires-at': '2099-01-01T00:00:00Z',
  'revoked-at': null,
  'last-used-at': null,
}
const expiredKey: ApiKeyInfo = {
  ...activeKey,
  id: 'expired',
  name: 'Read-only key',
  owner: 'Richard',
  'expires-at': '2000-01-01T00:00:00Z',
}
const revokedKey: ApiKeyInfo = {
  ...activeKey,
  id: 'revoked',
  name: 'Legacy automation',
  owner: 'Lena',
  'revoked-at': '2026-01-02T00:00:00Z',
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('ApiKeyTable loading state', () => {
  it('keeps the table shell and renders placeholder rows while keys load', () => {
    const wrapper = mount(ApiKeyTable, { props: { keys: [], loading: true } })

    expect(wrapper.find('.table-card').exists()).toBe(true)
    expect(wrapper.findAll('tbody tr')).toHaveLength(5)
    expect(wrapper.findAll('.skeleton-block')).toHaveLength(40)
    expect(wrapper.find('.apikey-empty').exists()).toBe(false)
    wrapper.unmount()
  })
})

describe('ApiKeyTable scope', () => {
  it('offers every user\'s keys only to those who can view them', async () => {
    const own = mount(ApiKeyTable, { props: { keys: [] } })
    expect(own.find('.table-head').text()).toContain('Your keys')
    expect(own.text()).not.toContain('All users')
    own.unmount()

    const wrapper = mount(ApiKeyTable, { props: { keys: [], canViewAll: true, showAll: false, 'onUpdate:showAll': (value: boolean) => wrapper.setProps({ showAll: value }) } })
    await wrapper.findAll('input[type="checkbox"]')[1].setValue(true)
    expect(wrapper.emitted('update:showAll')).toEqual([[true]])
    expect(wrapper.find('.table-head').text()).toContain('All keys')
    wrapper.unmount()
  })
})

describe('ApiKeyTable interactions', () => {
  it('filters inactive keys and shows an empty-search message', async () => {
    const wrapper = mount(ApiKeyTable, { props: { keys: [activeKey, expiredKey, revokedKey] } })

    expect(wrapper.findAll('tbody .name').map((name) => name.text())).toEqual(['My pipeline'])

    await wrapper.get('.apikey-check input').setValue(false)
    expect(wrapper.findAll('tbody .name').map((name) => name.text())).toEqual([
      'My pipeline',
      'Read-only key',
      'Legacy automation',
    ])

    await wrapper.get('.apikey-search').setValue('richard')
    expect(wrapper.findAll('tbody .name').map((name) => name.text())).toEqual(['Read-only key'])

    await wrapper.get('.apikey-search').setValue('no matching key')
    expect(wrapper.findAll('tbody .name')).toHaveLength(0)
    expect(wrapper.find('.apikey-empty').text()).toBe('No key matches these filters.')
    wrapper.unmount()
  })

  it('emits a revoke event only when revocation is confirmed', async () => {
    const wrapper = mount(ApiKeyTable, { props: { keys: [activeKey] } })
    const confirm = vi.spyOn(window, 'confirm')

    confirm.mockReturnValueOnce(false)
    await wrapper.get('.btn-danger').trigger('click')
    expect(wrapper.emitted('revoke')).toBeUndefined()

    confirm.mockReturnValueOnce(true)
    await wrapper.get('.btn-danger').trigger('click')
    expect(confirm).toHaveBeenNthCalledWith(1, 'Revoke "My pipeline"? Anything using it will stop working immediately.')
    expect(wrapper.emitted('revoke')).toEqual([[activeKey]])
    wrapper.unmount()
  })
})
