import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import AssetForm from './AssetForm.vue'
import { SECRET_CHECKER_KEY, type SecretChecker } from './secretChecker'
import type { AssetFormValidity } from './validity'

/* Shaped as GET /api/modules/{id}/schema serves it; the server tests that every module's schema has this shape */
const scenario = (type: string, title: string, properties: Record<string, unknown>, required: string[] = []) => ({
  type: 'object',
  title,
  properties: { type: { const: type, default: type }, applicability: { $ref: '#/$defs/Filter' }, ...properties },
  required: ['type', ...required],
})
const kubernetesStatic = {
  $schema: 'https://json-schema.org/draft/2020-12/schema',
  type: 'object',
  properties: {
    profile: {
      type: 'object',
      properties: {
        instanceType: { type: 'string', minLength: 1 },
        lifespan: { type: 'string', pattern: '^\\d+\\s*[dmyhDMYH]?$', description: 'Expected lifespan — duration as an amount and a unit suffix, e.g. 5y' },
        provider: { type: 'string', enum: ['aws', 'scaleway'] },
        scenarios: {
          type: 'array',
          items: {
            oneOf: [
              scenario('constant', 'Constant', { load: { type: 'number' } }),
              scenario('user_activity', 'User activity', { openingTime: { type: 'string', format: 'time' }, closingTime: { type: 'string', format: 'time' } }, ['openingTime', 'closingTime']),
              scenario('batch_activity', 'Batch activity', { triggerTime: { type: 'string', format: 'time' }, duration: { type: 'string', format: 'duration' } }, ['triggerTime', 'duration']),
            ],
            default: { type: 'constant' },
          },
        },
      },
      required: ['instanceType', 'lifespan', 'provider'],
    },
  },
  required: ['profile'],
  $defs: { Filter: { type: 'object', properties: { daysOfWeek: { type: 'array', items: { type: 'string', enum: ['MONDAY', 'FRIDAY'] } } } } },
}
const llmAnthropic = {
  $schema: 'https://json-schema.org/draft/2020-12/schema',
  type: 'object',
  properties: {
    adminKey: { type: 'string', format: 'secret-key', pattern: '^[A-Za-z0-9_-]+$', description: 'Secret holding an Admin API key' },
    apiUrl: { type: 'string', default: 'https://api.anthropic.com' },
    profile: {
      type: 'object',
      properties: {
        location: { type: 'string', oneOf: [{ const: 'USA', title: 'United States' }, { const: 'FRA', title: 'France' }], default: 'USA' },
        outputTokensPerRequest: { type: 'integer', default: 500, exclusiveMinimum: 0 },
        workspaceIds: { type: 'array', items: { type: 'string' } },
      },
    },
  },
  required: ['adminKey', 'profile'],
}

const anthropicProperties = {
  adminKey: 'ANTHROPIC_ADMIN_KEY',
  profile: { location: 'USA', outputTokensPerRequest: 500, workspaceIds: ['wrkspc_01'] },
}

let wrapper: VueWrapper | null = null

function render(schema: Record<string, unknown>, data: Record<string, unknown>, options: { checker?: SecretChecker; readonly?: boolean; showErrors?: boolean } = {}) {
  const model = ref(data)
  const validity = ref<AssetFormValidity | null>(null)
  const Host = defineComponent({
    setup() {
      return () => h(AssetForm, {
        schema,
        modelValue: model.value,
        readonly: options.readonly,
        showErrors: options.showErrors,
        'onUpdate:modelValue': (value: Record<string, unknown>) => { model.value = value },
        onValidity: (value: AssetFormValidity) => { validity.value = value },
      })
    },
  })
  wrapper = mount(Host, {
    attachTo: document.body,
    global: { provide: options.checker ? { [SECRET_CHECKER_KEY as symbol]: options.checker } : {} },
  })
  return { wrapper, model, validity }
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
  vi.useRealTimers()
})

describe('AssetForm', () => {
  it('renders a control per property, with its description', async () => {
    const { wrapper } = render(kubernetesStatic, { profile: {} }, { showErrors: false })
    await flushPromises()

    const labels = wrapper.findAll('.control > .label').map(label => label.text())
    expect(labels).toEqual(expect.arrayContaining([expect.stringContaining('Instance Type'), expect.stringContaining('Lifespan'), expect.stringContaining('Provider')]))
    expect(wrapper.text()).toContain('e.g. 5y')
    expect(wrapper.find('select option[value="scaleway"]').exists()).toBe(true)
  })

  it('reports the validity of the properties', async () => {
    const invalid = render(llmAnthropic, {})
    await flushPromises()
    expect(invalid.validity.value?.valid).toBe(false)
    wrapper?.unmount()

    const valid = render(llmAnthropic, anthropicProperties)
    await flushPromises()
    expect(valid.validity.value).toEqual({ valid: true, errors: [] })
  })

  it('accepts the time and duration values Java serializes', async () => {
    const { validity } = render(kubernetesStatic, {
      profile: {
        provider: 'aws', instanceType: 'c5a.4xlarge', lifespan: '5y',
        scenarios: [
          { type: 'user_activity', openingTime: '08:00', closingTime: '18:00:00' },
          { type: 'batch_activity', triggerTime: '02:00:00', duration: 'PT2H' },
        ],
      },
    })
    await flushPromises()
    expect(validity.value).toEqual({ valid: true, errors: [] })
  })

  it('shows each scenario as a card, with its kind and its own fields', async () => {
    const { wrapper } = render(kubernetesStatic, {
      profile: { scenarios: [{ type: 'batch_activity', triggerTime: '02:00:00', duration: 'PT2H' }] },
    })
    await flushPromises()

    const card = wrapper.find('.af-card')
    expect(card.find('.af-card-title').text()).toBe('Scenario 1')
    const kind = card.find('.af-card-variant')
    expect(kind.findAll('option').map(option => option.text())).toEqual(['Constant', 'User activity', 'Batch activity'])
    expect((kind.element as HTMLSelectElement).value).toBe('2')
    expect(card.findAll('.control > .label').map(label => label.text())).toEqual(expect.arrayContaining([expect.stringContaining('Trigger Time'), expect.stringContaining('Duration')]))
  })

  it('adds new scenarios as the first kind', async () => {
    const { wrapper, model } = render(kubernetesStatic, { profile: { scenarios: [] } })
    await flushPromises()

    await wrapper.find('.af-array-add').trigger('click')
    await flushPromises()

    expect((model.value.profile as { scenarios: unknown[] }).scenarios).toEqual([{ type: 'constant' }])
    expect(wrapper.find('.af-array-add').text()).toBe('+ Add scenario')
  })

  it('switches the kind of a scenario, confirming only when it would lose settings', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { wrapper, model } = render(kubernetesStatic, { profile: { scenarios: [{ type: 'constant' }, { type: 'constant', load: 0.5 }] } })
    await flushPromises()
    const scenarios = () => (model.value.profile as { scenarios: unknown[] }).scenarios

    await wrapper.findAll('.af-card-variant')[0].setValue('1')
    expect(confirm).not.toHaveBeenCalled()
    expect(scenarios()[0]).toEqual({ type: 'user_activity' })

    await wrapper.findAll('.af-card-variant')[1].setValue('2')
    expect(confirm).toHaveBeenCalledOnce()
    expect(scenarios()[1]).toEqual({ type: 'constant', load: 0.5 })
    expect((wrapper.findAll('.af-card-variant')[1].element as HTMLSelectElement).value).toBe('0')
    confirm.mockRestore()
  })

  it('reorders and removes scenarios', async () => {
    const { wrapper, model } = render(kubernetesStatic, { profile: { scenarios: [{ type: 'constant', load: 0.1 }, { type: 'constant', load: 0.2 }] } })
    await flushPromises()
    const loads = () => (model.value.profile as { scenarios: { load: number }[] }).scenarios.map(scenario => scenario.load)

    await wrapper.find('[aria-label="Move scenario 2 up"]').trigger('click')
    expect(loads()).toEqual([0.2, 0.1])
    await wrapper.find('[aria-label="Remove scenario 1"]').trigger('click')
    expect(loads()).toEqual([0.1])
  })

  it('lists plain values as fields', async () => {
    const { wrapper, model } = render(llmAnthropic, anthropicProperties)
    await flushPromises()
    const workspaces = () => (model.value.profile as { workspaceIds: string[] }).workspaceIds

    await wrapper.find('.af-array-add-link').trigger('click')
    await flushPromises()
    expect(document.activeElement).toBe(wrapper.find('[aria-label="Workspace id 2"]').element)
    await wrapper.find('[aria-label="Workspace id 2"]').setValue('wrkspc_02')
    expect(workspaces()).toEqual(['wrkspc_01', 'wrkspc_02'])
    await wrapper.find('[aria-label="Remove workspace id 1"]').trigger('click')
    expect(workspaces()).toEqual(['wrkspc_02'])
  })

  it('toggles options, in their listed order', async () => {
    const { wrapper, model } = render(kubernetesStatic, { profile: { scenarios: [{ type: 'constant' }] } })
    await flushPromises()
    const days = () => ((model.value.profile as { scenarios: { applicability?: { daysOfWeek: string[] } }[] }).scenarios[0].applicability?.daysOfWeek)

    const pills = wrapper.findAll('.af-pill')
    expect(pills.map(pill => pill.text())).toEqual(['Monday', 'Friday'])
    await pills[1].trigger('click')
    await pills[0].trigger('click')
    expect(days()).toEqual(['MONDAY', 'FRIDAY'])
    expect(pills[0].attributes('aria-pressed')).toBe('true')
    await pills[1].trigger('click')
    expect(days()).toEqual(['MONDAY'])
  })

  it('keeps times as typed, and shows those served with seconds', async () => {
    const { wrapper, model } = render(kubernetesStatic, { profile: { scenarios: [{ type: 'user_activity', openingTime: '08:00:00', closingTime: '18:30' }] } })
    await flushPromises()
    const [opening, closing] = wrapper.findAll('input[type="time"]')

    expect((opening.element as HTMLInputElement).value).toBe('08:00')
    expect((closing.element as HTMLInputElement).value).toBe('18:30')
    expect(opening.attributes('step')).toBe('60')
    ;(opening.element as HTMLInputElement).value = '09:30'
    await opening.trigger('change')
    expect((model.value.profile as { scenarios: { openingTime: string }[] }).scenarios[0].openingTime).toBe('09:30')
    expect((opening.element as HTMLInputElement).value).toBe('09:30')
  })

  it('reports text and numbers as they are typed', async () => {
    const { wrapper, model } = render(llmAnthropic, anthropicProperties)
    await flushPromises()
    const apiUrl = wrapper.find('[id="#/properties/apiUrl-input"]')
    const tokens = wrapper.findAll('.control').find(control => control.find('.label').text().startsWith('Output Tokens Per Request'))!.find('input')

    ;(apiUrl.element as HTMLInputElement).value = 'https://proxy.example.com'
    await apiUrl.trigger('input')
    expect(model.value.apiUrl).toBe('https://proxy.example.com')

    ;(tokens.element as HTMLInputElement).value = '750'
    await tokens.trigger('input')
    expect((model.value.profile as { outputTokensPerRequest: number }).outputTokensPerRequest).toBe(750)
    expect(tokens.attributes('step')).toBe('1')
  })

  it('does not rewrite a number being typed', async () => {
    const { wrapper, model } = render(kubernetesStatic, { profile: { scenarios: [{ type: 'constant', load: 0.1 }] } })
    await flushPromises()
    const load = wrapper.find('[id="#/properties/load-input"]')
    const field = load.element as HTMLInputElement

    field.focus()
    field.value = '0.50'
    await load.trigger('input')
    await flushPromises()

    expect((model.value.profile as { scenarios: { load: number }[] }).scenarios[0].load).toBe(0.5)
    expect(field.value).toBe('0.50')
    expect(load.attributes('step')).toBe('any')
  })

  it('keeps a time typed in until blur, whatever renders meanwhile', async () => {
    const { wrapper, model } = render(kubernetesStatic, { profile: { scenarios: [{ type: 'user_activity', openingTime: '08:00:00' }] } })
    await flushPromises()
    const opening = wrapper.find('input[type="time"]')
    const field = opening.element as HTMLInputElement
    const openingTime = () => (model.value.profile as { scenarios: { openingTime?: string }[] }).scenarios[0].openingTime

    // Safari: typed without input nor change event, then focus leaves, which renders the control again
    field.focus()
    await opening.trigger('focus')
    field.value = '10:45'
    await flushPromises()
    field.blur()
    await opening.trigger('blur')
    await flushPromises()

    expect(field.value).toBe('10:45')
    expect(openingTime()).toBe('10:45')
  })

  it('shows a time changed elsewhere unless it is being typed in', async () => {
    const { wrapper, model } = render(kubernetesStatic, { profile: { scenarios: [{ type: 'user_activity', openingTime: '08:00' }] } })
    await flushPromises()
    const field = wrapper.find('input[type="time"]').element as HTMLInputElement

    model.value = { profile: { scenarios: [{ type: 'user_activity', openingTime: '07:15:00' }] } }
    await flushPromises()
    expect(field.value).toBe('07:15')

    field.focus()
    field.value = '11:00'
    model.value = { profile: { scenarios: [{ type: 'user_activity', openingTime: '06:00' }] } }
    await flushPromises()
    expect(field.value).toBe('11:00')
  })

  it('edits nothing when read only', async () => {
    const { wrapper } = render(llmAnthropic, anthropicProperties, { readonly: true })
    await flushPromises()
    expect(wrapper.findAll('input').every(input => (input.element as HTMLInputElement).disabled)).toBe(true)
  })
})

describe('secret key control', () => {
  it('checks whether the secret name resolves', async () => {
    vi.useFakeTimers()
    const checker = vi.fn<SecretChecker>(async key => key === 'ANTHROPIC_ADMIN_KEY')
    const { wrapper } = render(llmAnthropic, anthropicProperties, { checker })
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()

    expect(checker).toHaveBeenCalledWith('ANTHROPIC_ADMIN_KEY')
    expect(wrapper.find('.af-secret-status').classes()).toContain('found')

    await wrapper.find('.af-secret input').setValue('OTHER_KEY')
    expect(wrapper.find('.af-secret-status').classes()).toContain('checking')
    expect(wrapper.find('.af-spinner').exists()).toBe(true)
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(wrapper.find('.af-secret-status').classes()).toContain('missing')
    expect(wrapper.find('.af-secret-status').attributes('title')).toBe('No secret named OTHER_KEY is known: declare it before saving')
    expect(wrapper.find('.af-secret-status').attributes('aria-label')).toBe(wrapper.find('.af-secret-status').attributes('title'))
  })

  it('flags invalid names without asking the server', async () => {
    const checker = vi.fn<SecretChecker>(async () => true)
    const { wrapper } = render(llmAnthropic, { ...anthropicProperties, adminKey: 'not/valid' }, { checker })
    await flushPromises()

    expect(wrapper.find('.af-secret-status').classes()).toContain('invalid')
    expect(checker).not.toHaveBeenCalled()
  })

  it('shows no status without a checker', async () => {
    const { wrapper } = render(llmAnthropic, anthropicProperties)
    await flushPromises()
    expect(wrapper.find('.af-secret input').exists()).toBe(true)
    expect(wrapper.find('.af-secret-status').exists()).toBe(false)
  })
})
