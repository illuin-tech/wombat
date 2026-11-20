import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import MarkdownView from './MarkdownView.vue'

let wrapper: VueWrapper

afterEach(() => wrapper.unmount())

describe('MarkdownView', () => {
  it('shows the source blurred until the renderer is loaded', async () => {
    wrapper = mount(MarkdownView, { props: { source: 'A **lab**' } })

    expect(wrapper.find('.markdown-view-source.blur-pending').text()).toBe('A **lab**')
    expect(wrapper.attributes('aria-busy')).toBe('true')

    await vi.waitFor(() => expect(wrapper.find('.markdown-view-content strong').exists()).toBe(true))
    expect(wrapper.find('.markdown-view-content').classes()).toContain('blur-reveal')
    expect(wrapper.find('.markdown-view-source').exists()).toBe(false)
    expect(wrapper.attributes('aria-busy')).toBe('false')
  })

  it('renders at once once the renderer is loaded', () => {
    wrapper = mount(MarkdownView, { props: { source: '*again*' } })
    expect(wrapper.find('.markdown-view-content em').text()).toBe('again')
    expect(wrapper.find('.markdown-view-content').classes()).not.toContain('blur-reveal')
  })

  it('escapes HTML and opens links in a new tab', () => {
    wrapper = mount(MarkdownView, { props: { source: '<script>alert(1)</script> https://example.com' } })

    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.text()).toContain('<script>alert(1)</script>')
    const link = wrapper.find('a')
    expect(link.attributes()).toMatchObject({ href: 'https://example.com', target: '_blank', rel: 'noopener noreferrer' })
  })
})
