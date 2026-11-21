import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import type { EnvironmentInfo } from '@/domain/environment'
import type { ModuleMetadata } from '@/domain/module'
import type { SimulationAssetInfo, SimulationInfo } from '@/domain/simulation'
import { fetchEnvironments } from '@/services/api/environments'
import { fetchModules, fetchModuleSchema } from '@/services/api/modules'
import {
  createSimulationAsset,
  deleteSimulation,
  fetchSimulation,
  fetchSimulationAsset,
  syncSimulationAsset,
  updateSimulation,
} from '@/services/api/simulations'
import SimulationPage from './SimulationPage.vue'

const session = vi.hoisted(() => ({ setPermissions: (_permissions: string[]): void => {} }))

vi.mock('@/services/auth/session', async () => {
  const { readonly, ref } = await import('vue')
  const appInfo = ref<unknown>(null)
  session.setPermissions = permissions => {
    appInfo.value = { auth: { enabled: true }, user: { authenticated: true, name: 'someone', roles: [], permissions }, ui: { maxDateRange: { millis: 0, label: '' } } }
  }
  return { useAppInfo: () => ({ appInfo: readonly(appInfo) }), loadAppInfo: vi.fn() }
})
vi.mock('@/services/api/environments', () => ({ fetchEnvironments: vi.fn() }))
vi.mock('@/services/api/modules', () => ({ fetchModules: vi.fn(), fetchModuleSchema: vi.fn() }))
vi.mock('@/services/api/secrets', () => ({ checkSecretKey: vi.fn() }))
vi.mock('@/services/api/simulations', () => ({
  fetchSimulation: vi.fn(), updateSimulation: vi.fn(), deleteSimulation: vi.fn(), fetchSimulationAsset: vi.fn(),
  createSimulationAsset: vi.fn(), updateSimulationAsset: vi.fn(), deleteSimulationAsset: vi.fn(), syncSimulationAsset: vi.fn(),
}))

const MANAGER = ['ui.simulations.read', 'ui.simulations.manage', 'api.simulation.write']
const READER = ['ui.simulations.read']

const llmStatic: ModuleMetadata = {
  type: { namespace: 'n', localName: 'llm-static', name: 'n.llm-static', regime: 'MODELED', family: 'LLM' },
  assetClass: 'LLMStaticAsset', enabled: true, creatable: true,
}
const schema = { type: 'object', properties: { profile: { type: 'object', properties: { zoneId: { type: 'string', default: 'UTC' } } } } }

const copy: SimulationAssetInfo = {
  id: 'chatbot', name: 'Chatbot', type: llmStatic.type.name, simulationId: 'what-if', environmentId: 'simulation:what-if',
  parentAssetId: 'chatbot', parentEnvironmentId: 'prod', parentStatus: 'in-sync', profile: { zoneId: 'UTC' },
}
const orphan: SimulationAssetInfo = { ...copy, id: 'legacy', name: 'Legacy', parentAssetId: 'legacy', parentStatus: 'parent-removed' }
const whatIf: SimulationInfo = {
  id: 'what-if', name: 'What if', description: 'A **bigger** chatbot', notes: 'Assumes 10k users', referenceWindow: 'MONTH',
  parentEnvironmentId: 'prod', parentEnvironmentName: 'Production', assets: [copy, orphan],
}
const production: EnvironmentInfo = { id: 'prod', name: 'Production', assets: [{ id: 'chatbot', name: 'Chatbot', type: llmStatic.type.name }] }

let wrapper: VueWrapper
let router: Router

async function render(permissions: string[], simulation: SimulationInfo = whatIf): Promise<void> {
  session.setPermissions(permissions)
  vi.mocked(fetchSimulation).mockResolvedValue(simulation)
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/simulations', name: 'simulations', component: { template: '<div />' } },
      { path: '/simulations/:id', name: 'simulation', component: SimulationPage },
      { path: '/environments/:id', name: 'environment', component: { template: '<div />' } },
    ],
  })
  await router.push(`/simulations/${simulation.id}`)
  wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
}

function click(selector: string): void {
  document.querySelector<HTMLButtonElement>(selector)!.click()
}

beforeEach(() => {
  vi.mocked(fetchModules).mockResolvedValue([llmStatic])
  vi.mocked(fetchModuleSchema).mockResolvedValue(schema)
  vi.mocked(fetchEnvironments).mockResolvedValue([production])
})

afterEach(() => {
  wrapper.unmount()
  document.body.innerHTML = ''
  vi.clearAllMocks()
  vi.useRealTimers()
})

describe('SimulationPage', () => {
  it('shows the simulation read only to readers, with where its assets come from', async () => {
    await render(READER)

    expect(wrapper.find('h2.page-title').text()).toBe('What if')
    expect(wrapper.find('.sim-parent').text()).toBe('From Production')
    expect(wrapper.find('#referenceWindow').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.env-delete').exists()).toBe(false)
    expect(wrapper.find('.asset-card-add').exists()).toBe(false)
    expect(wrapper.findAll('.asset-card .sim-badge').map(badge => badge.text())).toEqual(['Copy', 'Original removed'])
    expect(wrapper.find('.collapsible-notes-toggle').text()).toContain('Show')
  })

  it('says when the source environment was removed', async () => {
    await render(READER, { ...whatIf, parentEnvironmentName: null })

    expect(wrapper.find('.sim-parent.removed').text()).toBe('Source environment removed')
  })

  it('saves the name, notes and reference window once their edition pauses', async () => {
    vi.mocked(updateSimulation).mockImplementation(async (_id, fields) => ({ ...whatIf, ...fields }))
    await render(MANAGER)
    vi.useFakeTimers()

    await wrapper.find('.env-name-input').setValue('Bigger what if')
    await wrapper.find('#referenceWindow').setValue('YEAR')
    await vi.advanceTimersByTimeAsync(1500)

    expect(updateSimulation).toHaveBeenCalledExactlyOnceWith('what-if', {
      name: 'Bigger what if', description: 'A **bigger** chatbot', notes: 'Assumes 10k users', referenceWindow: 'YEAR',
    })
    expect(wrapper.find('.env-save-status').text()).toBe('All changes saved')
  })

  it('copies an asset of any environment, then opens the copy', async () => {
    const created: SimulationAssetInfo = { ...copy, id: 'chatbot-2' }
    vi.mocked(createSimulationAsset).mockResolvedValue(created)
    vi.mocked(fetchSimulationAsset).mockResolvedValue(created)
    await render(MANAGER)

    await wrapper.find('.asset-card-add').trigger('click')
    click('[data-source="existing"]')
    await flushPromises()
    expect(document.querySelector('.sim-copy-group')!.textContent).toContain('Production')
    click('.sim-copy-option')
    await flushPromises()

    expect(createSimulationAsset).toHaveBeenCalledExactlyOnceWith('what-if', { name: 'Chatbot', parentAssetId: 'chatbot' })
    expect(wrapper.findAll('.asset-card:not(.asset-card-add)')).toHaveLength(3)
    expect(document.querySelector('.asset-modal-overlay:not([hidden]) .asset-modal-title')!.textContent).toBe('Chatbot')
  })

  it('offers a sync when the copy diverged from its parent, once confirmed', async () => {
    const diverged: SimulationAssetInfo = { ...copy, parentStatus: 'diverged' }
    vi.mocked(fetchSimulationAsset).mockResolvedValue(diverged)
    vi.mocked(syncSimulationAsset).mockResolvedValue(copy)
    await render(MANAGER)

    await wrapper.findAll('.asset-card')[0].trigger('click')
    await flushPromises()
    expect(fetchSimulationAsset).toHaveBeenCalledWith('what-if', 'chatbot')
    expect(document.body.textContent).toContain('The original asset changed since it was copied')

    click('.sim-sync')
    await flushPromises()
    expect(syncSimulationAsset).not.toHaveBeenCalled()
    click('.confirm-dialog-submit')
    await flushPromises()

    expect(syncSimulationAsset).toHaveBeenCalledExactlyOnceWith('what-if', 'chatbot')
    expect(document.body.textContent).not.toContain('The original asset changed since it was copied')
  })

  it('tells a copy whose parent was deleted cannot be synced', async () => {
    vi.mocked(fetchSimulationAsset).mockResolvedValue(orphan)
    await render(MANAGER)

    await wrapper.findAll('.asset-card')[1].trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('The original asset was deleted')
    expect(document.querySelector('.sim-sync')).toBeNull()
  })

  it('deletes the simulation once its id is typed', async () => {
    vi.mocked(deleteSimulation).mockResolvedValue()
    await render(MANAGER)

    await wrapper.find('.env-delete').trigger('click')
    const input = document.querySelector<HTMLInputElement>('.confirm-delete-input')!
    input.value = 'what-if'
    input.dispatchEvent(new Event('input'))
    await flushPromises()
    click('.confirm-delete-submit')
    await flushPromises()

    expect(deleteSimulation).toHaveBeenCalledExactlyOnceWith('what-if')
    expect(router.currentRoute.value.fullPath).toBe('/simulations')
  })
})
