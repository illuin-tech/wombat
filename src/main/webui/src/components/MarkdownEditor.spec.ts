import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import MarkdownEditor from './MarkdownEditor.vue'

let wrapper: VueWrapper

function render(initial: string, disabled = false) {
  const source = ref(initial)
  wrapper = mount(defineComponent({
    setup: () => () => h(MarkdownEditor, {
      modelValue: source.value,
      'onUpdate:modelValue': (value: string) => { source.value = value },
      label: 'Description',
      disabled,
    }),
  }), { attachTo: document.body })
  return source
}

afterEach(() => {
  wrapper.unmount()
  document.body.innerHTML = ''
})

describe('MarkdownEditor', () => {
  it('shows the markdown rendered, and its source once clicked', async () => {
    const source = render('A **lab**, see https://example.com')
    await vi.waitFor(() => expect(wrapper.find('.markdown-view strong').text()).toBe('lab'))
    expect(wrapper.find('textarea').exists()).toBe(false)

    await wrapper.find('.markdown-editor-view').trigger('click')
    await flushPromises()
    const textarea = wrapper.find('textarea')
    expect((textarea.element as HTMLTextAreaElement).value).toBe('A **lab**, see https://example.com')
    expect(document.activeElement).toBe(textarea.element)

    await textarea.setValue('A *changed* lab')
    expect(source.value).toBe('A *changed* lab')
    await textarea.trigger('blur')
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.find('.markdown-view em').text()).toBe('changed')
  })

  it('leaves the source on Escape', async () => {
    render('Text')
    await wrapper.find('.markdown-editor-edit').trigger('click')
    await flushPromises()
    await wrapper.find('textarea').trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('textarea').exists()).toBe(false)
  })

  it('follows links rather than editing', async () => {
    render('See https://example.com')
    await vi.waitFor(() => expect(wrapper.find('.markdown-view a').exists()).toBe(true))
    await wrapper.find('.markdown-view a').trigger('click')
    expect(wrapper.find('textarea').exists()).toBe(false)
  })

  it('invites to write a description when there is none', () => {
    render('')
    expect(wrapper.find('.markdown-editor-empty').text()).toContain('Add a description')
  })

  it('cannot be edited when disabled', async () => {
    render('', true)
    expect(wrapper.find('.markdown-editor-empty').text()).toBe('No description.')
    expect(wrapper.find('.markdown-editor-edit').exists()).toBe(false)
    await wrapper.find('.markdown-editor-view').trigger('click')
    expect(wrapper.find('textarea').exists()).toBe(false)
  })
})
