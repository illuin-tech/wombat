import {afterEach, describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import DropdownPicker from '@/components/DropdownPicker.vue'

describe('DropdownPicker', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('shows the provided summary and opens/closes with the expected classes', async () => {
    const wrapper = mount(DropdownPicker, {
      props: {
        label: 'Assets',
        options: [{value: 'a', label: 'Asset A'}, {value: 'b', label: 'Asset B'}],
        modelValue: ['a'],
        summary: '1 / 2 assets',
        pickerId: 'assetPicker',
      },
      attachTo: document.body,
    })

    const trigger = wrapper.get('.container-trigger')
    expect(trigger.text()).toContain('1 / 2 assets')
    expect(wrapper.get('.container-dropdown').classes()).not.toContain('open')
    await trigger.trigger('click')
    expect(trigger.classes()).toContain('open')
    expect(wrapper.get('.container-dropdown').classes()).toContain('open')
    expect(wrapper.get('#assetDropdown')).toBeTruthy()

    document.body.dispatchEvent(new MouseEvent('click', {bubbles: true}))
    await wrapper.vm.$nextTick()
    expect(trigger.classes()).not.toContain('open')
    await trigger.trigger('click')
    document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}))
    await wrapper.vm.$nextTick()
    expect(trigger.classes()).not.toContain('open')

    wrapper.unmount()
  })

  it('selects and clears only visible results when searching', async () => {
    const wrapper = mount(DropdownPicker, {
      props: {
        label: 'Services',
        options: [{value: 'api', label: 'api'}, {value: 'worker', label: 'worker'}],
        modelValue: [],
        summary: '0 / 2 services',
        multiple: true,
        searchable: true,
        actions: true,
      },
      attachTo: document.body,
    })
    await wrapper.get('.container-trigger').trigger('click')
    const search = wrapper.get('.container-search')
    expect(document.activeElement).toBe(search.element)
    await search.setValue('api')
    await wrapper.get('#btnSelectAll').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([['api']])
    await wrapper.setProps({modelValue: ['api', 'worker']})
    await wrapper.get('#btnSelectNone').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([['worker']])
    wrapper.unmount()
  })
})
