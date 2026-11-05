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
