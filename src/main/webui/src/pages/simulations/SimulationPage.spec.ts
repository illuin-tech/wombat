import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import type { EnvironmentInfo } from '@/domain/environment'
import type { ModuleMetadata } from '@/domain/module'
import type { Footprint } from '@/domain/impact'
import type { Money, SimulationAssetInfo, SimulationEvaluation, SimulationInfo } from '@/domain/simulation'
import { fetchEnvironments } from '@/services/api/environments'
import { fetchModules, fetchModuleSchema } from '@/services/api/modules'
import {
  createSimulationAsset,
  deleteSimulation,
  evaluateSimulation,
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
  evaluateSimulation: vi.fn(),
}))

const MANAGER = ['ui.simulations.read', 'ui.simulations.manage', 'api.simulation.write']
const READER = ['ui.simulations.read']

const llmStatic: ModuleMetadata = {
  kind: 'asset',
  type: { namespace: 'n', localName: 'llm-static', name: 'n.llm-static', regime: 'MODELED', family: 'LLM' },
  assetClass: 'LLMStaticAsset', enabled: true, creatable: true,
}
const schema = { type: 'object', properties: { profile: { type: 'object', properties: { zoneId: { type: 'string', default: 'UTC' } } } } }

const SOURCE = { assetId: 'chatbot', environmentId: 'prod', status: 'in-sync' as const, syncedAt: '2026-11-20T10:00:00Z' }
const copy: SimulationAssetInfo = {
  id: 'chatbot', name: 'Chatbot', type: llmStatic.type.name, simulationId: 'what-if', source: SOURCE, profile: { zoneId: 'UTC' },
}
const orphan: SimulationAssetInfo = { ...copy, id: 'legacy', name: 'Legacy', source: { ...SOURCE, assetId: 'legacy', status: 'source-removed' } }
const whatIf: SimulationInfo = {
  id: 'what-if', name: 'What if', description: 'A **bigger** chatbot', notes: 'Assumes 10k users', referenceWindow: 'MONTH',
  sourceEnvironmentId: 'prod', sourceEnvironmentName: 'Production', assets: [copy, orphan],
}

function footprint(gwp: number): Footprint {
  const impact = (value: number, unit: string) => ({ unit, description: '', embedded: { value: 0 }, use: { value } })
  return { gwp: impact(gwp, 'kgCO2eq'), pe: impact(gwp * 10, 'MJ'), adp: impact(gwp / 10, 'kgSbeq') }
}
/* As the server scales the chatbot's one service */
const scaled = (perDay: number) => Object.fromEntries(([['DAY', 1], ['WEEK', 7], ['MONTH', 30], ['YEAR', 365]] as const).map(([period, days]) => [period, {
  impact: footprint(perDay * days),
  cost: [] as Money[],
  services: [{ serviceId: 'mistral-large', assetType: llmStatic.type, impact: footprint(perDay * days), cost: [] as Money[] }],
}])) as SimulationEvaluation['totals']
/* The chatbot's impact over the month-long window: 60 kgCO2eq */
const chatbotImpact = {
  type: 'IMPACT' as const,
  footprint: footprint(60),
  serviceImpacts: [{
    serviceId: 'mistral-large', assetType: llmStatic.type, footprint: footprint(60), profile: null,
    provider: 'mistral', model: 'mistral-large', location: 'FR', estimation: null, outputTokenCount: 100, requestCount: 10,
  }],
  provider: 'ECOLOGITS' as const,
  range: null,
}
const evaluation: SimulationEvaluation = {
  window: { start: '2026-10-22T00:00:00Z', end: '2026-11-21T00:00:00Z', period: 'MONTH' },
  factors: { DAY: 1 / 30, WEEK: 7 / 30, MONTH: 1, YEAR: 365 / 30 },
  totals: scaled(2),
  assets: [
    { id: 'chatbot', status: 'ok', coverage: 1, result: [chatbotImpact], scaled: scaled(2) },
    { id: 'legacy', status: 'no-data', message: 'No activity in the window', result: [], scaled: {} },
  ],
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

/* The reference window sits in the evaluation settings, behind the cog */
async function windowSelect(): Promise<HTMLSelectElement> {
  await wrapper.find('#evaluationSettings').trigger('click')
  return document.querySelector<HTMLSelectElement>('#referenceWindow')!
}

async function pickWindow(value: string): Promise<void> {
  const select = await windowSelect()
  select.value = value
  select.dispatchEvent(new Event('change'))
  await nextTick()
}

beforeEach(() => {
  vi.mocked(fetchModules).mockResolvedValue([llmStatic])
  vi.mocked(fetchModuleSchema).mockResolvedValue(schema)
  vi.mocked(fetchEnvironments).mockResolvedValue([production])
  vi.mocked(evaluateSimulation).mockResolvedValue(evaluation)
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
    expect(wrapper.find('.sim-source').text()).toBe('From Production')
    expect((await windowSelect()).disabled).toBe(true)
    expect(document.body.textContent).toContain('How the results are computed')
    expect(wrapper.find('.record-delete').exists()).toBe(false)
    expect(wrapper.find('.asset-card-add').exists()).toBe(false)
    expect(wrapper.findAll('.asset-card .status-chip').map(badge => badge.text())).toEqual(['Copy', 'Source removed'])
    expect(wrapper.find('.collapsible-notes-toggle').attributes('aria-expanded')).toBe('true')
    await vi.waitFor(() => expect(wrapper.find('.collapsible-notes .markdown-view').text()).toBe('Assumes 10k users'))
  })

  it('says when the source environment was removed', async () => {
    await render(READER, { ...whatIf, sourceEnvironmentName: null })

    expect(wrapper.find('.sim-source.removed').text()).toBe('Source environment removed')
  })

  it('saves the name, notes and reference window once their edition pauses', async () => {
    vi.mocked(updateSimulation).mockImplementation(async (_id, fields) => ({ ...whatIf, ...fields }))
    await render(MANAGER)
    vi.useFakeTimers()

    await wrapper.find('.record-name-input').setValue('Bigger what if')
    await pickWindow('YEAR')
    await vi.advanceTimersByTimeAsync(1500)

    expect(updateSimulation).toHaveBeenCalledExactlyOnceWith('what-if', {
      name: 'Bigger what if', description: 'A **bigger** chatbot', notes: 'Assumes 10k users', referenceWindow: 'YEAR',
    })
    expect(wrapper.find('.save-status').text()).toBe('All changes saved')
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

    expect(createSimulationAsset).toHaveBeenCalledExactlyOnceWith('what-if', { sourceAssetId: 'chatbot' })
    expect(wrapper.findAll('.asset-card:not(.asset-card-add)')).toHaveLength(3)
    expect(document.querySelector('.asset-modal-overlay:not([hidden]) .asset-modal-title')!.textContent).toBe('Chatbot')
  })

  it('offers a sync when the copy diverged from its source, once confirmed', async () => {
    const diverged: SimulationAssetInfo = { ...copy, source: { ...SOURCE, status: 'diverged' } }
    vi.mocked(fetchSimulationAsset).mockResolvedValue(diverged)
    vi.mocked(syncSimulationAsset).mockResolvedValue(copy)
    await render(MANAGER)

    await wrapper.findAll('.asset-card')[0].trigger('click')
    await flushPromises()
    expect(fetchSimulationAsset).toHaveBeenCalledWith('what-if', 'chatbot')
    expect(document.body.textContent).toContain('This copy and its source have diverged')

    click('.sim-sync')
    await flushPromises()
    expect(syncSimulationAsset).not.toHaveBeenCalled()
    click('.confirm-dialog-submit')
    await flushPromises()

    expect(syncSimulationAsset).toHaveBeenCalledExactlyOnceWith('what-if', 'chatbot')
    expect(document.body.textContent).not.toContain('This copy and its source have diverged')
  })

  it('tells a copy whose source was deleted cannot be synced', async () => {
    vi.mocked(fetchSimulationAsset).mockResolvedValue(orphan)
    await render(MANAGER)

    await wrapper.findAll('.asset-card')[1].trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('The source asset was deleted')
    expect(document.querySelector('.sim-sync')).toBeNull()
  })

  it('deletes the simulation once its id is typed', async () => {
    vi.mocked(deleteSimulation).mockResolvedValue()
    await render(MANAGER)

    await wrapper.find('.record-delete').trigger('click')
    const input = document.querySelector<HTMLInputElement>('.confirm-delete-input')!
    input.value = 'what-if'
    input.dispatchEvent(new Event('input'))
    await flushPromises()
    click('.confirm-delete-submit')
    await flushPromises()

    expect(deleteSimulation).toHaveBeenCalledExactlyOnceWith('what-if')
    expect(router.currentRoute.value.fullPath).toBe('/simulations')
  })

  it('evaluates the simulation once loaded, and switches periods without asking again', async () => {
    await render(READER)

    expect(evaluateSimulation).toHaveBeenCalledExactlyOnceWith('what-if', { scopes: ['IMPACT', 'COST'], referenceWindow: 'MONTH' })
    expect(wrapper.find('.sim-results-window').text()).toBe('Evaluated over Oct 22, 2026 → Nov 20, 2026 (UTC).')
    expect(wrapper.find('.kpi-card.green .kpi-value').text()).toBe('60')
    const rows = () => wrapper.findAll('.sim-results-table tbody tr').map(row => row.findAll('td').map(cell => cell.text()))
    expect(rows()).toEqual([
      ['Chatbot', 'Evaluated', '60', '600', '6', 'Not computed'],
      ['Legacy', 'No dataNo activity in the window', '—', '—', '—', '—'],
    ])
    expect(wrapper.find('.sim-status-message').text()).toBe('No activity in the window')

    await wrapper.findAll('#resultPeriods .chart-pill')[3].trigger('click')

    expect(evaluateSimulation).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.kpi-card.green .kpi-value').text()).toBe('730')
    expect(wrapper.find('.sim-results-window').text()).toContain('scaled to a year')
  })

  it('recomputes after a change of window, scope or asset, but not of name', async () => {
    vi.mocked(updateSimulation).mockImplementation(async (_id, fields) => ({ ...whatIf, ...fields }))
    vi.mocked(createSimulationAsset).mockResolvedValue({ ...copy, id: 'chatbot-2' })
    vi.mocked(fetchSimulationAsset).mockResolvedValue({ ...copy, id: 'chatbot-2' })
    await render(MANAGER)
    vi.useFakeTimers()

    await wrapper.find('.record-name-input').setValue('Bigger what if')
    await vi.advanceTimersByTimeAsync(2000)
    expect(evaluateSimulation).toHaveBeenCalledTimes(1)

    await pickWindow('WEEK')
    const [impactToggle] = wrapper.findAll('.sim-results .toggle-switch')
    await impactToggle.trigger('click')
    expect(wrapper.find('.sim-recomputing').text()).toBe('Recomputing…')
    await vi.advanceTimersByTimeAsync(500)
    expect(evaluateSimulation).toHaveBeenCalledTimes(2)
    expect(evaluateSimulation).toHaveBeenLastCalledWith('what-if', { scopes: ['COST'], referenceWindow: 'WEEK' })
    expect(wrapper.find('.kpi-row').exists()).toBe(false)

    await wrapper.find('.asset-card-add').trigger('click')
    click('[data-source="existing"]')
    await vi.advanceTimersByTimeAsync(0)
    click('.sim-copy-option')
    await vi.advanceTimersByTimeAsync(500)
    expect(evaluateSimulation).toHaveBeenCalledTimes(3)
  })

  it('breaks the results down per service with the dashboard\'s charts and table', async () => {
    await render(READER)

    await wrapper.findAll('#resultBreakdowns .chart-pill')[1].trigger('click')

    expect(wrapper.find('.sim-results-table').exists()).toBe(false)
    expect(wrapper.find('.sim-unevaluated').text()).toBe('One asset has no results: see By asset.')
    expect(wrapper.findAll('.sim-charts-row .chart-card')).toHaveLength(4)
    expect(wrapper.find('.service-summary .id').text()).toBe('mistral-large')
    expect(wrapper.find('.service-summary .metric.gwp.bold').text()).toBe('60')

    await wrapper.findAll('#resultPeriods .chart-pill')[0].trigger('click')

    expect(wrapper.find('.service-summary .metric.gwp.bold').text()).toBe('2')
    expect(evaluateSimulation).toHaveBeenCalledTimes(1)
  })
})
