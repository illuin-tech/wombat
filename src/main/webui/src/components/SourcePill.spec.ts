import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SourcePill from './SourcePill.vue'

describe('SourcePill', () => {
  it('shows a core module with a package', () => {
    const pill = mount(SourcePill, { props: { source: 'core' } })

    expect(pill.text()).toBe('Core')
    expect(pill.classes()).toEqual(['icon-pill', 'core'])
    expect(pill.findAll('svg path')).toHaveLength(4)
  })

  it('shows an extension module with a puzzle piece', () => {
    const pill = mount(SourcePill, { props: { source: 'extension' } })

    expect(pill.text()).toBe('Extension')
    expect(pill.attributes('title')).toBe('Loaded from a jar of the extension folder')
    expect(pill.findAll('svg path')).toHaveLength(1)
  })
})
