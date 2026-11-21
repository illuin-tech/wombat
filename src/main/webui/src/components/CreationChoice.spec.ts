import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CreationChoice from './CreationChoice.vue'

describe('CreationChoice', () => {
  it('offers both sources, and tells which one is chosen', async () => {
    const wrapper = mount(CreationChoice, {
      props: { existingTitle: 'From an existing environment', existingSubtitle: 'Copy its assets', scratchTitle: 'From scratch', scratchSubtitle: 'Start empty' },
    })

    expect(wrapper.findAll('.creation-choice-title').map(title => title.text())).toEqual(['From an existing environment', 'From scratch'])
    await wrapper.find('[data-source="scratch"]').trigger('click')
    await wrapper.find('[data-source="existing"]').trigger('click')

    expect(wrapper.emitted('choose')).toEqual([['scratch'], ['existing']])
  })
})
