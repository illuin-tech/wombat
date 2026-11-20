import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import CollapsibleNotes from './CollapsibleNotes.vue'

let wrapper: VueWrapper

function render(initial: string, disabled = false) {
  const notes = ref(initial)
  wrapper = mount(defineComponent({
    setup: () => () => h(CollapsibleNotes, {
      modelValue: notes.value,
      'onUpdate:modelValue': (value: string) => { notes.value = value },
      disabled,
    }),
  }), { attachTo: document.body })
  return notes
}

afterEach(() => {
  wrapper.unmount()
  document.body.innerHTML = ''
})

describe('CollapsibleNotes', () => {
  it('is folded by default, and says whether there is anything to read', async () => {
    render('')
    const toggle = wrapper.find('.collapsible-notes-toggle')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(toggle.text()).toContain('None yet')
    expect(wrapper.find('.markdown-editor').exists()).toBe(false)

    wrapper.unmount()
    render('Assumes **10k** users')
    expect(wrapper.find('.collapsible-notes-toggle').text()).toContain('Show')
  })

  it('unfolds the notes rendered, and edits them in place', async () => {
    const notes = render('Assumes **10k** users')
    await wrapper.find('.collapsible-notes-toggle').trigger('click')
    const toggle = wrapper.find('.collapsible-notes-toggle')
    expect(toggle.attributes('aria-expanded')).toBe('true')
    expect(wrapper.find(`#${toggle.attributes('aria-controls')}`).exists()).toBe(true)
    await vi.waitFor(() => expect(wrapper.find('.markdown-view strong').text()).toBe('10k'))

    await wrapper.find('.markdown-editor-view').trigger('click')
    await flushPromises()
    await wrapper.find('textarea').setValue('Assumes 20k users')
    expect(notes.value).toBe('Assumes 20k users')

    await wrapper.find('.collapsible-notes-toggle').trigger('click')
    expect(wrapper.find('.markdown-editor').exists()).toBe(false)
  })

  it('says there are no notes when they cannot be edited', async () => {
    render('', true)
    await wrapper.find('.collapsible-notes-toggle').trigger('click')

    expect(wrapper.find('.markdown-editor-empty').text()).toBe('No notes.')
    expect(wrapper.find('.markdown-editor-edit').exists()).toBe(false)
  })
})
