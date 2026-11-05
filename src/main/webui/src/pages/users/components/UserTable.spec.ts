import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import UserTable from './UserTable.vue'

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
