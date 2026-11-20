import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import type { EnvironmentInfo } from '@/domain/environment'
import type { ModuleMetadata } from '@/domain/module'
import { fetchEnvironments, fetchLiveEnvironments } from '@/services/api/environments'
import { fetchModules } from '@/services/api/modules'
import EnvironmentsPage from './EnvironmentsPage.vue'

const session = vi.hoisted(() => ({ setPermissions: (_permissions: string[]): void => {} }))

vi.mock('@/services/auth/session', async () => {
  const { readonly, ref } = await import('vue')
  const appInfo = ref<unknown>(null)
  session.setPermissions = permissions => {
    appInfo.value = { auth: { enabled: true }, user: { authenticated: true, name: 'someone', roles: [], permissions }, ui: { maxDateRange: { millis: 0, label: '' } } }
  }
  return { useAppInfo: () => ({ appInfo: readonly(appInfo) }), loadAppInfo: vi.fn() }
})
vi.mock('@/services/api/environments', () => ({ fetchEnvironments: vi.fn(), fetchLiveEnvironments: vi.fn() }))
vi.mock('@/services/api/modules', () => ({ fetchModules: vi.fn() }))

const llm: ModuleMetadata = {
  type: { namespace: 'n', localName: 'llm-static', name: 'n.llm-static', regime: 'MODELED', family: 'LLM' },
  assetClass: 'LLMStaticAsset', enabled: true, creatable: true,
}
const environments: EnvironmentInfo[] = [
  { id: 'preprod', name: 'Pre-production', provisioned: true, assets: [{ id: 'my-llm', type: 'n.llm-static' }, { id: 'other', type: 'n.llm-static' }] },
  { id: 'lab', name: 'Lab', disabledAt: '2026-01-01T00:00:00Z', assets: [] },
]

let wrapper: VueWrapper
let router: Router

async function render(permissions: string[]): Promise<void> {
  session.setPermissions(permissions)
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/environments', name: 'environments', component: EnvironmentsPage },
      { path: '/environments/new', name: 'environment-create', component: { template: '<div />' } },
      { path: '/environments/:id', name: 'environment', component: { template: '<div />' } },
      { path: '/impact/:environment', name: 'impact', component: { template: '<div />' } },
    ],
  })
  await router.push('/environments')
  wrapper = mount(EnvironmentsPage, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
}

beforeEach(() => {
  vi.mocked(fetchEnvironments).mockResolvedValue(environments)
  vi.mocked(fetchModules).mockResolvedValue([llm])
  vi.mocked(fetchLiveEnvironments).mockResolvedValue([{ id: 'preprod', assets: [] }])
})

afterEach(() => {
  wrapper.unmount()
  document.body.innerHTML = ''
  vi.clearAllMocks()
})

describe('EnvironmentsPage', () => {
  it('lists environments with their status, lock and asset counts', async () => {
    await render(['ui.environments.read'])

    const rows = wrapper.findAll('.env-row')
    expect(rows.map(row => row.find('.env-row-id').text())).toEqual(['lab', 'preprod'])
    expect(rows[0].find('.env-status').text()).toBe('Disabled')
    expect(rows[1].find('.lock-badge').exists()).toBe(true)
    expect(rows[1].find('.asset-type-tag.llm').text()).toBe('2')
  })

  it('only links live environments to the dashboard', async () => {
    await render(['ui.environments.read'])

    const [lab, preprod] = wrapper.findAll('.env-row')
    expect(lab.find('.dashboard-link').classes()).toContain('disabled')
    expect(preprod.find('a.dashboard-link').attributes('href')).toBe('/impact/preprod')
  })

  it('filters by name and status', async () => {
    await render(['ui.environments.read'])

    await wrapper.find('.search-input').setValue('pre')
    expect(wrapper.findAll('.env-row')).toHaveLength(1)
    await wrapper.find('.search-input').setValue('nothing like it')
    expect(wrapper.find('.table-empty').text()).toBe('No environment matches these filters.')
  })

  it('opens an environment from its row', async () => {
    await render(['ui.environments.read'])

    await wrapper.findAll('.env-row')[1].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/environments/preprod')
  })

  it('offers creation to those who may create environments', async () => {
    await render(['ui.environments.read'])
    expect(wrapper.find('#newEnvironment').exists()).toBe(false)
    wrapper.unmount()

    await render(['ui.environments.read', 'ui.environments.manage', 'api.environment.write'])
    expect(wrapper.find('#newEnvironment').attributes('href')).toBe('/environments/new')
  })
})
