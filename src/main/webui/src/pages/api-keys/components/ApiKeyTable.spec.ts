import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ApiKeyTable from './ApiKeyTable.vue'

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
