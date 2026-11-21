import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import type { EnvironmentInfo } from '@/domain/environment'
import type { ModuleMetadata } from '@/domain/module'
import type { SimulationInfo } from '@/domain/simulation'
import { fetchEnvironments } from '@/services/api/environments'
import { fetchModules } from '@/services/api/modules'
import { createSimulation } from '@/services/api/simulations'
import NewSimulationPage from './NewSimulationPage.vue'

vi.mock('@/services/api/environments', () => ({ fetchEnvironments: vi.fn() }))
vi.mock('@/services/api/modules', () => ({ fetchModules: vi.fn() }))
vi.mock('@/services/api/simulations', () => ({ createSimulation: vi.fn() }))

const llmStatic: ModuleMetadata = {
  type: { namespace: 'n', localName: 'llm-static', name: 'n.llm-static', regime: 'MODELED', family: 'LLM' },
  assetClass: 'LLMStaticAsset', enabled: true, creatable: true,
}
const vertex: ModuleMetadata = {
  type: { namespace: 'n', localName: 'llm-vertexai', name: 'n.llm-vertexai', regime: 'MEASURED', family: 'LLM' },
  assetClass: 'LLMVertexAIAsset', enabled: false, creatable: true,
}
const production: EnvironmentInfo = {
  id: 'prod', name: 'Production', assets: [
    { id: 'chatbot', name: 'Chatbot', type: llmStatic.type.name },
    { id: 'legacy', name: 'Legacy', type: llmStatic.type.name, disabledAt: '2026-11-01T00:00:00Z' },
    { id: 'gemini', name: 'Gemini', type: vertex.type.name },
  ],
}

let wrapper: VueWrapper
let router: Router

async function render(): Promise<void> {
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/simulations', name: 'simulations', component: { template: '<div />' } },
      { path: '/simulations/new', name: 'simulation-create', component: NewSimulationPage },
      { path: '/simulations/:id', name: 'simulation', component: { template: '<div />' } },
    ],
  })
  await router.push('/simulations/new')
  wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
}

beforeEach(() => {
  vi.mocked(fetchEnvironments).mockResolvedValue([production])
  vi.mocked(fetchModules).mockResolvedValue([llmStatic, vertex])
  vi.mocked(createSimulation).mockImplementation(async body => ({ ...body, referenceWindow: 'MONTH', assets: [] }) as SimulationInfo)
})

afterEach(() => {
  wrapper.unmount()
  document.body.innerHTML = ''
  vi.clearAllMocks()
})

describe('NewSimulationPage', () => {
  it('copies the picked assets of an environment', async () => {
    await render()
    await wrapper.find('[data-source="existing"]').trigger('click')

    await wrapper.find('#parentEnvironment').setValue('prod')
    const boxes = wrapper.findAll<HTMLInputElement>('.sim-asset-checklist input')
    expect(boxes.map(box => [box.element.value, box.element.checked, box.element.disabled])).toEqual([
      ['chatbot', true, false],
      ['gemini', false, true],
      ['legacy', false, false],
    ])
    expect(wrapper.find('.sim-asset-checklist').text()).toContain('Module disabled')
    expect((wrapper.find('#simulationName').element as HTMLInputElement).value).toBe('Production what-if')
    await boxes[2].setValue(true)

    await wrapper.find('#createSimulation').trigger('click')
    await flushPromises()

    expect(createSimulation).toHaveBeenCalledExactlyOnceWith({
      id: 'production-what-if', name: 'Production what-if', description: null, parentEnvironmentId: 'prod', assetIds: ['chatbot', 'legacy'],
    })
    expect(router.currentRoute.value.fullPath).toBe('/simulations/production-what-if')
  })

  it('creates an empty simulation from scratch', async () => {
    await render()
    await wrapper.find('[data-source="scratch"]').trigger('click')
    expect(wrapper.find('#parentEnvironment').exists()).toBe(false)

    await wrapper.find('#simulationName').setValue('Chatbot launch')
    await wrapper.find('#createSimulation').trigger('click')
    await flushPromises()

    expect(createSimulation).toHaveBeenCalledExactlyOnceWith({ id: 'chatbot-launch', name: 'Chatbot launch', description: null })
  })

  it('asks for the environment to copy', async () => {
    await render()
    await wrapper.find('[data-source="existing"]').trigger('click')
    await wrapper.find('#simulationName').setValue('Nothing picked')

    await wrapper.find('#createSimulation').trigger('click')
    await flushPromises()

    expect(createSimulation).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Pick the environment to copy.')
  })
})
