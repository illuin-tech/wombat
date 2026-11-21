import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import type { EnvironmentInfo } from '@/domain/environment'
import type { ModuleMetadata } from '@/domain/module'
import { createAsset, updateAsset } from '@/services/api/assets'
import { createEnvironment, fetchEnvironment, fetchLiveEnvironments, updateEnvironment } from '@/services/api/environments'
import { fetchModules, fetchModuleSchema } from '@/services/api/modules'
import { checkSecretKey } from '@/services/api/secrets'
import EnvironmentPage from './EnvironmentPage.vue'

const session = vi.hoisted(() => ({ setPermissions: (_permissions: string[]): void => {} }))

vi.mock('@/services/auth/session', async () => {
  const { readonly, ref } = await import('vue')
  const appInfo = ref<unknown>(null)
  session.setPermissions = permissions => {
    appInfo.value = { auth: { enabled: true }, user: { authenticated: true, name: 'someone', roles: [], permissions }, ui: { maxDateRange: { millis: 0, label: '' } } }
  }
  return { useAppInfo: () => ({ appInfo: readonly(appInfo) }), loadAppInfo: vi.fn() }
})
vi.mock('@/services/api/environments', () => ({
  fetchEnvironment: vi.fn(), fetchLiveEnvironments: vi.fn(), createEnvironment: vi.fn(), updateEnvironment: vi.fn(), deleteEnvironment: vi.fn(),
}))
vi.mock('@/services/api/assets', () => ({ createAsset: vi.fn(), updateAsset: vi.fn(), deleteAsset: vi.fn() }))
vi.mock('@/services/api/modules', () => ({ fetchModules: vi.fn(), fetchModuleSchema: vi.fn() }))
vi.mock('@/services/api/secrets', () => ({ checkSecretKey: vi.fn() }))

/* The type's schema, as GET /api/modules/{id}/schema serves it */
const llmAnthropicSchema = {
  $schema: 'https://json-schema.org/draft/2020-12/schema',
  type: 'object',
  properties: {
    adminKey: { type: 'string', format: 'secret-key', pattern: '^[A-Za-z0-9_-]+$' },
    apiUrl: { type: 'string', default: 'https://api.anthropic.com' },
    profile: {
      type: 'object',
      properties: { location: { type: 'string', default: 'USA' }, outputTokensPerRequest: { type: 'integer', default: 500 } },
    },
  },
  required: ['adminKey', 'profile'],
}

const ADMIN = ['ui.environments.read', 'ui.environments.manage', 'api.environment.write', 'api.asset.write', 'api.secret.read']
const READER = ['ui.environments.read']

const anthropic: ModuleMetadata = {
  type: { namespace: 'tech.illuin.wombat-module', localName: 'llm-anthropic', name: 'tech.illuin.wombat-module.llm-anthropic', regime: 'MEASURED', family: 'LLM' },
  assetClass: 'LLMAnthropicAsset', enabled: true, creatable: true,
}
const disabled: ModuleMetadata = {
  type: { namespace: 'tech.illuin.wombat-module', localName: 'llm-vertexai', name: 'tech.illuin.wombat-module.llm-vertexai', regime: 'MEASURED', family: 'LLM' },
  assetClass: 'LLMVertexAIAsset', enabled: false, creatable: true,
}
const simulated: ModuleMetadata = {
  type: { namespace: 'tech.illuin.wombat-module', localName: 'llm-simulated', name: 'tech.illuin.wombat-module.llm-simulated', regime: 'MODELED', family: 'LLM' },
  assetClass: 'LLMSimulatedAsset', enabled: true, creatable: false,
}
const anthropicAsset = {
  id: 'claude', name: 'Claude usage', type: anthropic.type.name, environmentId: 'lab', provisioned: false,
  createdAt: '2026-11-01T00:00:00Z', updatedAt: '2026-11-02T00:00:00Z',
  adminKey: 'ANTHROPIC_ADMIN_KEY', apiUrl: null, profile: { location: 'USA', outputTokensPerRequest: 500 },
}
const lab: EnvironmentInfo = {
  id: 'lab', name: 'Lab', description: 'A **lab**', provisioned: false,
  createdAt: '2026-11-01T00:00:00Z', updatedAt: '2026-11-02T00:00:00Z', assets: [anthropicAsset],
}
const empty: EnvironmentInfo = { ...lab, id: 'empty', name: 'Empty', assets: [] }
const provisioned: EnvironmentInfo = { ...lab, id: 'preprod', name: 'Preprod', provisioned: true }

let wrapper: VueWrapper
let router: Router

async function render(path: string, permissions: string[]): Promise<void> {
  session.setPermissions(permissions)
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/environments', name: 'environments', component: { template: '<div />' } },
      { path: '/environments/new', name: 'environment-create', component: EnvironmentPage },
      { path: '/environments/:id', name: 'environment', component: EnvironmentPage },
      { path: '/impact/:environment', name: 'impact', component: { template: '<div />' } },
    ],
  })
  await router.push(path)
  wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
}

beforeEach(() => {
  vi.mocked(fetchModules).mockResolvedValue([anthropic, disabled, simulated])
  vi.mocked(fetchModuleSchema).mockResolvedValue(llmAnthropicSchema)
  vi.mocked(fetchLiveEnvironments).mockResolvedValue([{ id: 'lab', assets: [] }])
  vi.mocked(checkSecretKey).mockImplementation(async key => ({ key, exists: true }))
  vi.mocked(fetchEnvironment).mockImplementation(async id => [lab, empty, provisioned].find(env => env.id === id)!)
})

afterEach(() => {
  wrapper.unmount()
  document.body.innerHTML = ''
  vi.clearAllMocks()
  vi.useRealTimers()
})

describe('EnvironmentPage', () => {
  it('shows the environment read only to readers', async () => {
    await render('/environments/lab', READER)

    expect(wrapper.find('.record-name-input').exists()).toBe(false)
    expect(wrapper.find('h2.page-title').text()).toBe('Lab')
    await vi.waitFor(() => expect(wrapper.find('.markdown-view strong').text()).toBe('lab'))
    expect(wrapper.find('.toggle-switch').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.record-delete').exists()).toBe(false)
    expect(wrapper.find('.asset-card-add').exists()).toBe(false)
    expect(wrapper.findAll('.asset-card')).toHaveLength(1)
  })

  it('locks provisioned environments, even for admins', async () => {
    await render('/environments/preprod', ADMIN)

    expect(wrapper.find('.record-id-line .lock-badge').exists()).toBe(true)
    expect(wrapper.find('.record-name-input').exists()).toBe(false)
    expect(wrapper.find('.asset-card-add').exists()).toBe(false)
  })

  it('saves the environment once its edition pauses', async () => {
    vi.mocked(updateEnvironment).mockImplementation(async (id, fields) => ({ ...lab, ...fields, updatedAt: '2026-11-20T00:00:00Z' }))
    await render('/environments/lab', ADMIN)
    vi.useFakeTimers()

    await wrapper.find('.record-name-input').setValue('Laboratory')
    await wrapper.find('.toggle-switch').trigger('click')
    expect(wrapper.find('.save-status').text()).toBe('Unsaved changes')
    await vi.advanceTimersByTimeAsync(1500)

    expect(updateEnvironment).toHaveBeenCalledExactlyOnceWith('lab', { name: 'Laboratory', description: 'A **lab**', disabled: true })
    expect(wrapper.find('.save-status').text()).toBe('All changes saved')
  })

  it('does not save a blank name', async () => {
    await render('/environments/lab', ADMIN)
    vi.useFakeTimers()

    await wrapper.find('.record-name-input').setValue('  ')
    await vi.advanceTimersByTimeAsync(2000)

    expect(updateEnvironment).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('A name is required.')
  })

  it('only deletes environments without assets', async () => {
    await render('/environments/lab', ADMIN)
    expect(wrapper.find('.record-delete').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.record-delete-wrapper').attributes('title')).toBe('Delete its 1 asset first to delete this environment')
    expect(wrapper.text()).not.toContain('Delete its')
    wrapper.unmount()

    await render('/environments/empty', ADMIN)
    expect(wrapper.find('.record-delete').attributes('disabled')).toBeUndefined()
    expect(wrapper.find('.record-delete-wrapper').attributes('title')).toBeUndefined()
    await wrapper.find('.record-delete').trigger('click')
    const confirm = document.querySelector<HTMLButtonElement>('.confirm-delete-submit')!
    expect(confirm.disabled).toBe(true)
    const input = document.querySelector<HTMLInputElement>('.confirm-delete-input')!
    input.value = 'empty'
    input.dispatchEvent(new Event('input'))
    await flushPromises()
    expect(confirm.disabled).toBe(false)
  })

  it('creates an environment, then shows it', async () => {
    vi.mocked(createEnvironment).mockImplementation(async body => ({ ...empty, ...body, assets: [] }))
    await render('/environments/new', ADMIN)

    expect(wrapper.find('.asset-grid').exists()).toBe(false)
    await wrapper.find('.record-name-input').setValue('My Lab')
    expect((wrapper.find('.record-id-field input').element as HTMLInputElement).value).toBe('my-lab')
    await wrapper.find('#createEnvironment').trigger('click')
    await flushPromises()

    expect(createEnvironment).toHaveBeenCalledExactlyOnceWith({ id: 'my-lab', name: 'My Lab', description: null, disabled: false })
    expect(router.currentRoute.value.fullPath).toBe('/environments/my-lab')
    expect(wrapper.find('.asset-card-add').exists()).toBe(true)
  })

  it('creates an asset of a type picked among the creatable and enabled ones', async () => {
    vi.mocked(createAsset).mockImplementation(async body => ({ ...body, ...body.properties }))
    await render('/environments/empty', ADMIN)

    await wrapper.find('.asset-card-add').trigger('click')
    const options = [...document.querySelectorAll('.asset-type-option-name')].map(option => option.textContent)
    expect(options).toEqual(['llm-anthropic'])

    document.querySelector<HTMLButtonElement>('.asset-type-option')!.click()
    await flushPromises()
    expect(fetchModuleSchema).toHaveBeenCalledWith(anthropic.type.name)
    // The form generator loads with the first form shown
    await vi.waitFor(() => expect(document.querySelector('.af-secret input')).not.toBeNull())

    const setValue = async (selector: string, value: string) => {
      const input = document.querySelector<HTMLInputElement>(selector)!
      input.value = value
      input.dispatchEvent(new Event('input'))
      input.dispatchEvent(new Event('change'))
      await flushPromises()
    }
    await setValue('.asset-editor-fields input[type="text"]', 'Claude')
    await setValue('.af-secret input', 'ANTHROPIC_ADMIN_KEY')
    document.querySelector<HTMLButtonElement>('.asset-editor-save')!.click()
    await flushPromises()

    expect(createAsset).toHaveBeenCalledOnce()
    const body = vi.mocked(createAsset).mock.calls[0][0]
    expect(body).toMatchObject({ id: 'claude', environmentId: 'empty', type: anthropic.type.name, name: 'Claude', disabled: false })
    expect(body.properties).toMatchObject({ adminKey: 'ANTHROPIC_ADMIN_KEY', apiUrl: 'https://api.anthropic.com', profile: { location: 'USA', outputTokensPerRequest: 500 } })
    expect(wrapper.findAll('.asset-card:not(.asset-card-add)')).toHaveLength(1)
  })

  it('shows a blurred form while the asset type loads', async () => {
    let resolveSchema!: (schema: Record<string, unknown>) => void
    vi.mocked(fetchModuleSchema).mockReturnValue(new Promise(resolve => { resolveSchema = resolve }))
    await render('/environments/lab', ADMIN)

    await wrapper.find('.asset-card').trigger('click')
    await flushPromises()
    expect(document.querySelector('.asset-form-placeholder.blur-pending')).not.toBeNull()
    expect(document.querySelector('.asset-form')).toBeNull()

    resolveSchema(llmAnthropicSchema)
    await vi.waitFor(() => expect(document.querySelector('.asset-form.blur-reveal')).not.toBeNull())
    expect(document.querySelector('.asset-form-placeholder')).toBeNull()
  })

  it('offers the dashboard once the environment gets there', async () => {
    vi.mocked(createAsset).mockImplementation(async body => ({ ...body, ...body.properties }))
    vi.mocked(fetchLiveEnvironments).mockResolvedValue([])
    await render('/environments/empty', ADMIN)
    expect(wrapper.find('.dashboard-link').classes()).toContain('disabled')

    vi.mocked(fetchLiveEnvironments).mockResolvedValue([{ id: 'empty', assets: [] }])
    wrapper.findComponent({ name: 'AssetEditorModal' }).vm.$emit('saved', { ...anthropicAsset, environmentId: 'empty' })
    await flushPromises()

    expect(wrapper.find('a.dashboard-link').attributes('href')).toBe('/impact/empty')
  })

  it('offers saving as soon as a property is typed in, before leaving the field', async () => {
    await render('/environments/lab', ADMIN)

    await wrapper.find('.asset-card').trigger('click')
    await vi.waitFor(() => expect(document.querySelector('[id="#/properties/apiUrl-input"]')).not.toBeNull())
    const save = document.querySelector<HTMLButtonElement>('.asset-editor-save')!
    expect(save.disabled).toBe(true)

    const apiUrl = document.querySelector<HTMLInputElement>('[id="#/properties/apiUrl-input"]')!
    apiUrl.focus()
    apiUrl.value = 'https://proxy.example.com'
    apiUrl.dispatchEvent(new Event('input'))
    await flushPromises()

    expect(document.activeElement).toBe(apiUrl)
    expect(save.disabled).toBe(false)
  })

  it('asks before discarding the changes to an asset', async () => {
    await render('/environments/lab', ADMIN)
    await wrapper.find('.asset-card').trigger('click')
    await flushPromises()
    const shown = () => [...document.querySelectorAll<HTMLElement>('.asset-modal-overlay')].filter(overlay => !overlay.hidden)

    const name = document.querySelector<HTMLInputElement>('.asset-editor-fields input[type="text"]')!
    name.value = 'Claude org'
    name.dispatchEvent(new Event('input'))
    await flushPromises()
    shown()[0].querySelector<HTMLButtonElement>('.asset-modal-close')!.click()
    await flushPromises()
    expect(shown()).toHaveLength(2)

    document.querySelector<HTMLButtonElement>('.confirm-dialog-cancel')!.click()
    await flushPromises()
    expect(shown()).toHaveLength(1)

    shown()[0].querySelector<HTMLButtonElement>('.asset-modal-close')!.click()
    await flushPromises()
    shown()[1].querySelector('form')!.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(shown()).toHaveLength(0)
  })

  it('labels each of its dialogs by its own title', async () => {
    await render('/environments/lab', ADMIN)
    // The asset's deletion is confirmed by a second confirmation dialog
    await wrapper.find('.asset-card').trigger('click')
    await flushPromises()
    const dialogs = [...document.querySelectorAll('[role="dialog"]')]
    expect(dialogs).toHaveLength(4)

    const labels = dialogs.map(dialog => dialog.getAttribute('aria-labelledby')!)
    expect(new Set(labels).size).toBe(labels.length)
    for (const dialog of dialogs)
      expect(document.getElementById(dialog.getAttribute('aria-labelledby')!)?.closest('[role="dialog"]')).toBe(dialog)
  })

  it('saves an asset only once it changed', async () => {
    vi.mocked(updateAsset).mockImplementation(async (id, fields) => ({ ...anthropicAsset, ...fields, ...fields.properties }))
    await render('/environments/lab', ADMIN)

    await wrapper.find('.asset-card').trigger('click')
    await flushPromises()
    const save = document.querySelector<HTMLButtonElement>('.asset-editor-save')!
    expect(save.disabled).toBe(true)

    const name = document.querySelector<HTMLInputElement>('.asset-editor-fields input[type="text"]')!
    name.value = 'Claude org'
    name.dispatchEvent(new Event('input'))
    await flushPromises()
    expect(save.disabled).toBe(false)
    save.click()
    await flushPromises()

    expect(updateAsset).toHaveBeenCalledExactlyOnceWith('claude', {
      name: 'Claude org',
      description: null,
      disabled: false,
      properties: { adminKey: 'ANTHROPIC_ADMIN_KEY', profile: { location: 'USA', outputTokensPerRequest: 500 } },
    })
  })
})
