import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { AssetType } from '@/domain/environment'
import AssetTypeParam from './AssetTypeParam.vue'

const session = vi.hoisted(() => ({ setPermissions: (_permissions: string[]): void => {} }))

vi.mock('@/services/auth/session', async () => {
  const { readonly, ref } = await import('vue')
  const appInfo = ref<unknown>(null)
  session.setPermissions = permissions => {
    appInfo.value = { auth: { enabled: true }, user: { authenticated: true, name: 'someone', roles: [], permissions }, ui: { maxDateRange: { millis: 0, label: '' } } }
  }
  return { useAppInfo: () => ({ appInfo: readonly(appInfo) }), loadAppInfo: vi.fn() }
})

const type: AssetType = { namespace: 'tech.illuin.wombat-module', localName: 'llm-anthropic', name: 'tech.illuin.wombat-module.llm-anthropic', regime: 'MEASURED', family: 'LLM' }

async function render(permissions: string[]) {
  session.setPermissions(permissions)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'impact', component: { template: '<div />' } },
      { path: '/modules/:type', name: 'module', component: { template: '<div />' } },
    ],
  })
  await router.push('/')
  return mount(AssetTypeParam, { props: { type }, global: { plugins: [router] } })
}

describe('AssetTypeParam', () => {
  it('shows the full name only', async () => {
    const wrapper = await render([])
    expect(wrapper.find('code.tech-name').text()).toBe('tech.illuin.wombat-module.llm-anthropic')
    expect(wrapper.text()).toBe('Asset typetech.illuin.wombat-module.llm-anthropic')
  })

  it('links to the module page for a user who can read modules', async () => {
    const wrapper = await render(['ui.modules.read'])
    expect(wrapper.find('a.asset-type-link').attributes('href')).toBe('/modules/tech.illuin.wombat-module.llm-anthropic')
  })

  it('hides the link from a user who cannot read modules', async () => {
    const wrapper = await render([])
    expect(wrapper.find('.asset-type-link').exists()).toBe(false)
  })
})
