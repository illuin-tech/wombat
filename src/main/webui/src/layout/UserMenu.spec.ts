import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import UserMenu from './UserMenu.vue'

const session = vi.hoisted(() => ({ setPicture: (_picture: string | null): void => {} }))

vi.mock('@/services/auth/session', async () => {
  const { readonly, ref } = await import('vue')
  const appInfo = ref<unknown>(null)
  session.setPicture = picture => {
    appInfo.value = { auth: { enabled: true }, user: { authenticated: true, name: 'alice@example.com', picture, roles: ['user'], permissions: [] }, ui: { maxDateRange: { millis: 0, label: '' } } }
  }
  return { useAppInfo: () => ({ appInfo: readonly(appInfo) }) }
})

describe('UserMenu', () => {
  beforeEach(() => session.setPicture(null))

  it('shows the identity provider picture', () => {
    session.setPicture('https://lh3.googleusercontent.com/a/photo')
    const wrapper = mount(UserMenu)

    const avatars = wrapper.findAll('img.header-user-avatar')
    expect(avatars).toHaveLength(2)
    expect(avatars[0].attributes()).toMatchObject({ src: 'https://lh3.googleusercontent.com/a/photo', referrerpolicy: 'no-referrer' })
  })

  it('shows no picture when the provider sends none', () => {
    const wrapper = mount(UserMenu)

    expect(wrapper.find('img.header-user-avatar').exists()).toBe(false)
    expect(wrapper.find('.header-user-name').text()).toBe('alice@example.com')
  })

  it('hides a picture that fails to load', async () => {
    session.setPicture('https://example.com/gone.png')
    const wrapper = mount(UserMenu)

    await wrapper.find('img.header-user-avatar').trigger('error')

    expect(wrapper.find('img.header-user-avatar').exists()).toBe(false)
  })
})
