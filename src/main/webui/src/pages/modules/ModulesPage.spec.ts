import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { CoreDeploymentStatus, ModuleInfo } from '@/domain/module'
import { deleteModule, fetchDeployment, fetchModuleInfos, redeployCore } from '@/services/api/modules'
import ModulesPage from './ModulesPage.vue'

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
  fetchModuleInfos: vi.fn(), fetchDeployment: vi.fn(), deleteModule: vi.fn(), redeployCore: vi.fn(),
}))

const READER = ['ui.modules.read']
const MANAGER = ['ui.modules.read', 'ui.modules.manage', 'api.module.manage']

function module(localName: string, overrides: Partial<Omit<ModuleInfo, 'kind' | 'type'>> = {}): ModuleInfo {
  return {
    kind: 'asset',
    type: { namespace: 'tech.illuin', localName, name: `tech.illuin.${localName}`, regime: 'MEASURED', family: 'LLM' },
    assetClass: 'Asset', enabled: true, creatable: true, deployed: true, pending: false, missing: false, source: 'core', assetCount: 2,
    ...overrides,
  }
}

const anthropic = module('llm-anthropic')
const vertex = module('llm-vertexai', { enabled: false, pending: true })
const removed = module('acme-removed', { missing: true, assetClass: null, source: null, deployed: false, assetCount: 0 })

function deployment(version: number, overrides: Partial<CoreDeploymentStatus> = {}): CoreDeploymentStatus {
  return {
    latest: { version, id: `a3f9c21e8b0d${version}`, requestedAt: '2026-11-20T14:02:00Z', requestedBy: 'alice', modules: [] },
    instances: [{
      instanceId: 'wombat', deployedVersion: version, deployedId: `a3f9c21e8b0d${version}`, deployedAt: '2026-11-20T14:02:00Z',
      status: 'ok', error: null, detectedModules: [], missingModules: [], current: true,
    }],
    pending: [{ type: vertex.type.name, enabled: false }],
    pollIntervalSeconds: 30,
    ...overrides,
  }
}

let wrapper: VueWrapper

async function render(permissions: string[]): Promise<void> {
  session.setPermissions(permissions)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/modules', name: 'modules', component: ModulesPage },
      { path: '/modules/:type', name: 'module', component: { template: '<div />' } },
    ],
  })
  await router.push('/modules')
  wrapper = mount(ModulesPage, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
}

beforeEach(() => {
  vi.mocked(fetchModuleInfos).mockResolvedValue([removed, vertex, anthropic])
  vi.mocked(fetchDeployment).mockResolvedValue(deployment(7))
})

afterEach(() => {
  wrapper.unmount()
  document.body.innerHTML = ''
  vi.clearAllMocks()
})

describe('ModulesPage', () => {
  it('lists the modules with their states, missing ones last', async () => {
    await render(READER)

    const rows = wrapper.findAll('.module-row')
    expect(rows.map(row => row.find('.tech-name').text())).toEqual([anthropic.type.name, vertex.type.name, removed.type.name])
    expect(rows[1].findAll('.module-state').map(state => state.text())).toEqual(['Disabled', 'Not deployed'])
    expect(rows[2].classes()).toContain('inactive')
    expect(rows[2].find('.module-state').text()).toBe('Missing')
  })

  it('tells a family module apart, as it has no regime', async () => {
    const boavizta: ModuleInfo = {
      kind: 'family',
      type: { namespace: 'tech.illuin', localName: 'boavizta', name: 'tech.illuin.boavizta', family: 'KUBERNETES_CONTAINER' },
      assetClass: null, enabled: true, creatable: false, deployed: true, pending: false, missing: false, source: 'core', assetCount: 0,
    }
    vi.mocked(fetchModuleInfos).mockResolvedValue([anthropic, boavizta])
    await render(READER)

    const row = (name: string) => wrapper.findAll('.module-row').find(candidate => candidate.find('.tech-name').text() === name)!
    expect(row('tech.illuin.boavizta').find('.module-states').text()).toContain('Family')
    expect(row(anthropic.type.name).find('.module-states').text()).not.toContain('Family')
  })

  it('shows the running core by its short id, and the changes not deployed', async () => {
    await render(READER)

    expect(wrapper.find('.core-id').text()).toBe('a3f9c21')
    expect(wrapper.find('.core-status-line').text()).toContain('by alice')
    expect(wrapper.find('.module-pending-banner').text()).toBe(
      '1 change not deployed: a core redeployment is required for monitoring and evaluations to take it into account.',
    )
  })

  it('leaves redeploying and deleting to managers', async () => {
    await render(READER)

    expect(wrapper.find('#redeployCore').exists()).toBe(false)
    expect(wrapper.find('.module-delete').exists()).toBe(false)
  })

  it('deletes a missing module once its name is typed', async () => {
    vi.mocked(deleteModule).mockResolvedValue()
    await render(MANAGER)

    await wrapper.find('.module-delete').trigger('click')
    const input = document.querySelector<HTMLInputElement>('.confirm-delete-input')!
    input.value = removed.type.name
    input.dispatchEvent(new Event('input'))
    await flushPromises()
    document.querySelector<HTMLButtonElement>('.confirm-delete-submit')!.click()
    await flushPromises()

    expect(deleteModule).toHaveBeenCalledWith(removed.type.name)
    expect(fetchModuleInfos).toHaveBeenCalledTimes(2)
  })

  it('redeploys, then waits until every instance runs the new core', async () => {
    vi.mocked(redeployCore).mockResolvedValue({ version: 8, id: 'b71c0de99', local: { status: 'ok', error: null, added: [], removed: [vertex.type.name] } })
    await render(MANAGER)

    await wrapper.find('#redeployCore').trigger('click')
    expect(document.querySelector('.redeploy-changes')!.textContent).toContain(vertex.type.name)

    vi.mocked(fetchDeployment).mockResolvedValue(deployment(8, { pending: [] }))
    document.querySelector<HTMLButtonElement>('.redeploy-dialog .btn-apply')!.click()
    await flushPromises()

    expect(redeployCore).toHaveBeenCalled()
    expect(document.querySelector('.redeploy-dialog')!.textContent).toContain('Every instance runs core b71c0de')
  })
})
