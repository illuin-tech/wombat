import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import App from '@/App.vue'
import { router } from '@/router'

const api = vi.hoisted(() => ({
  fetchAppInfo: vi.fn(),
  fetchLiveEnvironments: vi.fn(),
}))

vi.mock('@/services/api/app', () => ({ fetchAppInfo: api.fetchAppInfo }))
vi.mock('@/services/api/environments', async importOriginal => ({
  ...await importOriginal<typeof import('@/services/api/environments')>(),
  fetchLiveEnvironments: api.fetchLiveEnvironments,
}))

function appInfo(permissions: string[]) {
  return {
    auth: { enabled: false },
    user: { authenticated: false, name: null, roles: [], permissions },
    ui: { maxDateRange: { millis: 90 * 86_400_000, label: '90 days' } },
  }
}

async function mountHome() {
  await router.push('/')
  await router.isReady()
  const wrapper = mount(App, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('HomeRedirectPage', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('points a fresh install to the Environments page rather than reporting an error', async () => {
    api.fetchAppInfo.mockResolvedValue(appInfo(['ui.dashboard.read', 'ui.environments.read', 'ui.environments.manage', 'api.environment.write']))
    api.fetchLiveEnvironments.mockResolvedValue([])

    const wrapper = await mountHome()

    expect(wrapper.find('.notice-title').text()).toBe('No environments yet')
    expect(wrapper.find('.notice-body a').attributes('href')).toBe('/environments')
  })

  it('reports an error when the environments cannot be fetched', async () => {
    api.fetchAppInfo.mockResolvedValue(appInfo(['ui.dashboard.read']))
    api.fetchLiveEnvironments.mockRejectedValue(new Error('unreachable'))

    const wrapper = await mountHome()

    expect(wrapper.find('.notice-title').text()).toBe('No environments available')
  })
})
