import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import type { SimulationInfo, SimulationSummary } from '@/domain/simulation'
import { duplicateSimulation, fetchSimulations } from '@/services/api/simulations'
import SimulationsPage from './SimulationsPage.vue'

const session = vi.hoisted(() => ({ setPermissions: (_permissions: string[]): void => {} }))

vi.mock('@/services/auth/session', async () => {
  const { readonly, ref } = await import('vue')
  const appInfo = ref<unknown>(null)
  session.setPermissions = permissions => {
    appInfo.value = { auth: { enabled: true }, user: { authenticated: true, name: 'someone', roles: [], permissions }, ui: { maxDateRange: { millis: 0, label: '' } } }
  }
  return { useAppInfo: () => ({ appInfo: readonly(appInfo) }), loadAppInfo: vi.fn() }
})
vi.mock('@/services/api/simulations', () => ({ fetchSimulations: vi.fn(), duplicateSimulation: vi.fn() }))

const MANAGER = ['ui.simulations.read', 'ui.simulations.manage', 'api.simulation.write']
const READER = ['ui.simulations.read']

const simulations: SimulationSummary[] = [
  { id: 'bigger', name: 'Bigger cluster', referenceWindow: 'MONTH', sourceEnvironmentId: 'prod', sourceEnvironmentName: 'Production', assetCount: 2 },
  { id: 'orphan', name: 'Orphan', referenceWindow: 'MONTH', sourceEnvironmentId: 'gone', sourceEnvironmentName: null, assetCount: 1 },
  { id: 'scratch', name: 'Scratch', referenceWindow: 'YEAR', assetCount: 0 },
]

let wrapper: VueWrapper
let router: Router

async function render(permissions: string[]): Promise<void> {
  session.setPermissions(permissions)
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/simulations', name: 'simulations', component: SimulationsPage },
      { path: '/simulations/new', name: 'simulation-create', component: { template: '<div />' } },
      { path: '/simulations/:id', name: 'simulation', component: { template: '<div />' } },
    ],
  })
  await router.push('/simulations')
  wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
}

beforeEach(() => {
  vi.mocked(fetchSimulations).mockResolvedValue(simulations)
})

afterEach(() => {
  wrapper.unmount()
  document.body.innerHTML = ''
  vi.clearAllMocks()
})

describe('SimulationsPage', () => {
  it('lists the simulations with their source environment, and filters them', async () => {
    await render(READER)

    const rows = wrapper.findAll('.record-row')
    expect(rows.map(row => row.find('.record-row-name').text())).toEqual(['Bigger cluster', 'Orphan', 'Scratch'])
    expect(rows[0].text()).toContain('Production')
    expect(rows[1].text()).toContain('Source environment removed')
    expect(rows[2].text()).toContain('None')
    expect(wrapper.find('#newSimulation').exists()).toBe(false)
    expect(wrapper.find('.sim-duplicate-action').exists()).toBe(false)

    await wrapper.find('.search-input').setValue('prod')
    expect(wrapper.findAll('.record-row')).toHaveLength(1)
  })

  it('opens a simulation', async () => {
    await render(READER)

    await wrapper.findAll('.record-row')[2].trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/simulations/scratch')
  })

  it('duplicates a simulation under a name derived from its own, then opens the duplicate', async () => {
    vi.mocked(duplicateSimulation).mockImplementation(async (_id, body) => ({ ...simulations[0], ...body, assets: [] }) as SimulationInfo)
    await render(MANAGER)
    expect(wrapper.find('#newSimulation').exists()).toBe(true)

    await wrapper.find('.sim-duplicate-action').trigger('click')
    await flushPromises()
    const name = document.querySelector<HTMLInputElement>('.sim-duplicate input[type="text"]')!
    expect(name.value).toBe('Bigger cluster (copy)')
    expect(document.querySelector<HTMLInputElement>('.sim-duplicate input.mono')!.value).toBe('bigger-cluster-copy')
    expect(router.currentRoute.value.fullPath).toBe('/simulations')

    document.querySelector<HTMLButtonElement>('.sim-duplicate-submit')!.click()
    await flushPromises()

    expect(duplicateSimulation).toHaveBeenCalledExactlyOnceWith('bigger', { id: 'bigger-cluster-copy', name: 'Bigger cluster (copy)' })
    expect(router.currentRoute.value.fullPath).toBe('/simulations/bigger-cluster-copy')
  })
})
