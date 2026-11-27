import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { CoreDeploymentStatus, ModuleInfo } from '@/domain/module'
import { fetchDeployment, fetchModule, fetchModuleSchema, setModuleEnabled } from '@/services/api/modules'
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
  fetchModule: vi.fn(), fetchDeployment: vi.fn(), setModuleEnabled: vi.fn(), deleteModule: vi.fn(), fetchModuleSchema: vi.fn(),
}))

const READER = ['ui.modules.read']
const MANAGER = ['ui.modules.read', 'ui.modules.manage', 'api.module.manage']

const anthropic: ModuleInfo = {
  kind: 'asset',
  type: { namespace: 'tech.illuin.wombat-module', localName: 'llm-anthropic', name: 'tech.illuin.wombat-module.llm-anthropic', regime: 'MEASURED', family: 'LLM' },
  assetClass: 'tech.illuin.LLMAnthropicAsset', enabled: true, creatable: true, deployed: true, pending: false, missing: false, source: 'core',
  ordering: 1, provides: ['source', 'activity'], assetCount: 3,
}

const deployment: CoreDeploymentStatus = {
  latest: { version: 7, id: 'a3f9c21e8b0d', requestedAt: '2026-11-20T14:02:00Z', requestedBy: null, modules: [anthropic.type.name] },
  instances: [], pending: [], moved: [], pollIntervalSeconds: 30,
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

const schema = {
  type: 'object',
  required: ['adminKey'],
  properties: { adminKey: { type: 'string', format: 'secret-key', description: 'Secret holding an Admin API key' } },
}

beforeEach(() => {
  vi.mocked(fetchDeployment).mockResolvedValue(deployment)
  vi.mocked(fetchModuleSchema).mockResolvedValue(schema)
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

  it('tells what the module provides, or that this instance cannot tell', async () => {
    await render(READER)

    const features = wrapper.findAll('.module-provides .module-feature')
    expect(features.map(feature => feature.text())).toEqual(['Source', 'Activity'])
    expect(features.map(feature => feature.classes())).toEqual([['module-feature', 'source'], ['module-feature', 'activity']])

    await wrapper.findAll('.module-provides .pill-hint')[1].trigger('mouseenter')
    expect(document.querySelector('.pill-hint-bubble')!.textContent).toContain('Resolves what the assets did over a time range')

    wrapper.unmount()
    await render(READER, { ...anthropic, provides: ['source', 'profile'] })
    expect(wrapper.findAll('.module-provides .module-feature').map(feature => feature.text())).toEqual(['Source', 'Profile'])

    wrapper.unmount()
    await render(READER, { ...anthropic, source: null, assetClass: null, provides: [] })
    expect(wrapper.find('.module-provides').exists()).toBe(false)
    expect(wrapper.find('.module-facts').text()).toMatch(/Provides\s*Unknown/)
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

  it('outlines the asset properties, or shows the raw schema', async () => {
    await render(READER)

    expect(fetchModuleSchema).toHaveBeenCalledWith(anthropic.type.name)
    expect(wrapper.find('.schema-property .schema-label').text()).toBe('Admin key')
    expect(wrapper.find('.schema-type').text()).toBe('secret reference')

    await wrapper.find('.module-schema .btn-link-inline').trigger('click')
    expect(JSON.parse(wrapper.find('.schema-raw').text())).toEqual(schema)
  })

  it('does not ask for the schema of a module this instance does not detect', async () => {
    await render(READER, { ...anthropic, assetClass: null, source: null, deployed: false })

    expect(fetchModuleSchema).not.toHaveBeenCalled()
    expect(wrapper.find('.module-schema').text()).toContain('This instance does not detect the module')
  })

  it('offers to delete a missing module instead of toggling it', async () => {
    await render(MANAGER, { ...anthropic, missing: true, assetClass: null, source: null, deployed: false })

    expect(wrapper.find('.toggle-switch').exists()).toBe(false)
    expect(wrapper.find('.module-state-block .btn-danger').exists()).toBe(true)
    expect(wrapper.find('.notice-card').text()).toContain('No instance detects this module any more')
  })
})
