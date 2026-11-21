import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import CollapsibleNotes from './CollapsibleNotes.vue'

let wrapper: VueWrapper

function render(initial: string, disabled = false, open = false) {
  const notes = ref(initial)
  wrapper = mount(defineComponent({
    setup: () => () => h(CollapsibleNotes, {
      modelValue: notes.value,
      'onUpdate:modelValue': (value: string) => { notes.value = value },
      disabled,
      open,
    }),
  }), { attachTo: document.body })
  return notes
}

afterEach(() => {
  wrapper.unmount()
  document.body.innerHTML = ''
  vi.unstubAllGlobals()
})

/* jsdom lays nothing out: every observed element reports this height */
function contentHeight(height: number): void {
  vi.stubGlobal('ResizeObserver', class {
    constructor(private readonly callback: ResizeObserverCallback) {}
    observe(): void { this.callback([{ contentRect: { height } } as ResizeObserverEntry], this as unknown as ResizeObserver) }
    disconnect(): void {}
  })
}

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

  it('starts unfolded when open', async () => {
    render('Assumes **10k** users', false, true)

    expect(wrapper.find('.collapsible-notes-toggle').attributes('aria-expanded')).toBe('true')
    await vi.waitFor(() => expect(wrapper.find('.markdown-view strong').text()).toBe('10k'))
  })

  it('cuts long notes until shown in full', async () => {
    contentHeight(600)
    render('A long story', false, true)
    await flushPromises()

    expect(wrapper.find('.collapsible-notes-frame').classes()).toContain('clamped')
    await wrapper.find('.collapsible-notes-more').trigger('click')
    expect(wrapper.find('.collapsible-notes-frame').classes()).not.toContain('clamped')
    expect(wrapper.find('.collapsible-notes-more').text()).toBe('Show less')
  })

  it('shows short notes whole, with nothing to click', async () => {
    contentHeight(120)
    render('A short note', false, true)
    await flushPromises()

    expect(wrapper.find('.collapsible-notes-frame').classes()).not.toContain('clamped')
    expect(wrapper.find('.collapsible-notes-more').exists()).toBe(false)
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
