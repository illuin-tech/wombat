import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { CoreDeploymentStatus, ModuleInfo } from '@/domain/module'
import { fetchDeployment, fetchModule, setModuleEnabled } from '@/services/api/modules'
import ModulePage from './ModulePage.vue'

const session = vi.hoisted(() => ({ setPermissions: (_permissions: string[]): void => {} }))

vi.mock('@/services/auth/session', async () => {
  const { readonly, ref } = await import('vue')
  const appInfo = ref<unknown>(null)
  session.setPermissions = permissions => {
    appInfo.value = { auth: { enabled: true }, user: { authenticated: true, name: 'someone', roles: [], permissions }, ui: { maxDateRange: { millis: 0, label: '' } } }
  }
  return { useAppInfo: () => ({ appInfo: readonly(appInfo) }), loadAppInfo: vi.fn() }
})
vi.mock('@/services/api/modules', () => ({
  fetchModule: vi.fn(), fetchDeployment: vi.fn(), setModuleEnabled: vi.fn(), deleteModule: vi.fn(),
}))

const READER = ['ui.modules.read']
const MANAGER = ['ui.modules.read', 'ui.modules.manage', 'api.module.manage']

const anthropic: ModuleInfo = {
  type: { namespace: 'tech.illuin.wombat-module', localName: 'llm-anthropic', name: 'tech.illuin.wombat-module.llm-anthropic', regime: 'MEASURED', family: 'LLM' },
  assetClass: 'tech.illuin.LLMAnthropicAsset', enabled: true, creatable: true, deployed: true, pending: false, missing: false, source: 'core', assetCount: 3,
}

const deployment: CoreDeploymentStatus = {
  latest: { version: 7, id: 'a3f9c21e8b0d', requestedAt: '2026-11-20T14:02:00Z', requestedBy: null, modules: [anthropic.type.name] },
  instances: [], pending: [], pollIntervalSeconds: 30,
}

let wrapper: VueWrapper

async function render(permissions: string[], module: ModuleInfo = anthropic): Promise<void> {
  session.setPermissions(permissions)
  vi.mocked(fetchModule).mockResolvedValue(module)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/modules', name: 'modules', component: { template: '<div />' } },
      { path: '/modules/:type', name: 'module', component: ModulePage },
    ],
  })
  await router.push(`/modules/${encodeURIComponent(module.type.name)}`)
  wrapper = mount(ModulePage, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
}

beforeEach(() => {
  vi.mocked(fetchDeployment).mockResolvedValue(deployment)
})

afterEach(() => {
  wrapper.unmount()
  document.body.innerHTML = ''
  vi.clearAllMocks()
})

describe('ModulePage', () => {
  it('describes the module, and whether the running core holds it', async () => {
    await render(READER)

    expect(fetchModule).toHaveBeenCalledWith(anthropic.type.name)
    expect(wrapper.find('.page-title').text()).toBe('llm-anthropic')
    expect(wrapper.find('.module-facts').text()).toContain('tech.illuin.LLMAnthropicAsset')
    expect(wrapper.find('.module-core-line').text()).toBe('Running in core a3f9c21 on this instance')
  })

  it('shows the toggle to managers only', async () => {
    await render(READER)
    expect(wrapper.find('.toggle-switch').exists()).toBe(false)

    wrapper.unmount()
    await render(MANAGER)
    expect(wrapper.find('.toggle-switch').exists()).toBe(true)
  })

  it('confirms disabling a module its assets use', async () => {
    vi.mocked(setModuleEnabled).mockResolvedValue({ ...anthropic, enabled: false, pending: true })
    await render(MANAGER)

    await wrapper.find('.toggle-switch').trigger('click')
    expect(setModuleEnabled).not.toHaveBeenCalled()
    expect(document.querySelector('.confirm-dialog-text')!.textContent).toContain('3 assets use this module')

    document.querySelector<HTMLButtonElement>('.confirm-dialog-submit')!.click()
    await flushPromises()

    expect(setModuleEnabled).toHaveBeenCalledWith(anthropic.type.name, false)
    expect(wrapper.find('.notice-card').text()).toContain(
      'Hidden in the asset pickers now. A core redeployment is required for monitoring and evaluations to take it into account',
    )
  })

  it('enables a module at once', async () => {
    vi.mocked(setModuleEnabled).mockResolvedValue({ ...anthropic, pending: true })
    await render(MANAGER, { ...anthropic, enabled: false, deployed: false })

    await wrapper.find('.toggle-switch').trigger('click')
    await flushPromises()

    expect(setModuleEnabled).toHaveBeenCalledWith(anthropic.type.name, true)
  })

  it('offers to delete a missing module instead of toggling it', async () => {
    await render(MANAGER, { ...anthropic, missing: true, assetClass: null, source: null, deployed: false })

    expect(wrapper.find('.toggle-switch').exists()).toBe(false)
    expect(wrapper.find('.module-state-block .btn-danger').exists()).toBe(true)
    expect(wrapper.find('.notice-card').text()).toContain('No instance detects this module any more')
  })
})
