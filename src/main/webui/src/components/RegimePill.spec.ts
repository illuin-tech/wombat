import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RegimePill from './RegimePill.vue'

describe('RegimePill', () => {
  it('shows a measured regime with a ruler', () => {
    const pill = mount(RegimePill, { props: { regime: 'MEASURED' } })

    expect(pill.text()).toBe('Measured')
    expect(pill.classes()).toContain('measured')
    expect(pill.findAll('svg path')).toHaveLength(5)
  })

  it('shows a modeled regime with geometric shapes', () => {
    const pill = mount(RegimePill, { props: { regime: 'MODELED' } })

    expect(pill.text()).toBe('Modeled')
    expect(pill.find('svg rect').exists()).toBe(true)
    expect(pill.find('svg circle').exists()).toBe(true)
  })

  it('shows an unknown regime without icon', () => {
    const pill = mount(RegimePill, { props: { regime: 'UNKNOWN' } })

    expect(pill.text()).toBe('Unknown')
    expect(pill.find('svg').exists()).toBe(false)
  })
})
