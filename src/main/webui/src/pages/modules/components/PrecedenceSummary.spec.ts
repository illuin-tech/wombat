import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import type { FeaturePrecedence, ModuleInfo, ModulePrecedence } from '@/domain/module'
import PrecedenceSummary from './PrecedenceSummary.vue'

const METRICS = 'tech.illuin.wombat-module.kubernetes-metrics'
const BOAVIZTA = 'tech.illuin.wombat-module.boavizta'
const ESTIMATOR = 'acme.estimator'

function module(name: string, localName: string): ModuleInfo {
  return {
    kind: 'family',
    type: { namespace: name.slice(0, name.lastIndexOf('.')), localName, name, family: 'KUBERNETES_CONTAINER' },
    assetClass: null, enabled: true, creatable: false, deployed: true, pending: false, missing: false, source: 'core',
    ordering: null, provides: [], assetCount: 0,
  }
}

const modules = [module(METRICS, 'kubernetes-metrics'), module(BOAVIZTA, 'boavizta'), module(ESTIMATOR, 'estimator')]

function precedence(changed: boolean, ...features: FeaturePrecedence[]): ModulePrecedence {
  return { families: [{ family: 'KUBERNETES_CONTAINER', features }], changed }
}

function render(summary: ModulePrecedence) {
  return mount(PrecedenceSummary, { props: { precedence: summary, modules }, attachTo: document.body })
}

afterEach(() => {
  document.body.innerHTML = ''
})

describe('PrecedenceSummary', () => {
  it('tells which module serves each component first, then the ones after it', () => {
    const wrapper = render(precedence(false,
      { feature: 'activity', deployed: [METRICS], next: [METRICS], changed: false },
      { feature: 'impact', deployed: [ESTIMATOR, BOAVIZTA], next: [ESTIMATOR, BOAVIZTA], changed: false },
    ))

    expect(wrapper.findAll('.precedence-panel')).toHaveLength(1)
    expect(wrapper.find('.precedence-arrow').exists()).toBe(false)
    const rows = wrapper.findAll('.precedence-row')
    expect(rows.map(row => row.find('.module-feature').text())).toEqual(['Activity', 'Impact'])
    expect(rows[1].find('.precedence-first').text()).toBe('estimator')
    expect(rows[1].find('.precedence-then').text()).toBe(', then boavizta')
  })

  it('compares the deployed precedence with the one after the redeploy, marking what changes', () => {
    const wrapper = render(precedence(true,
      { feature: 'activity', deployed: [METRICS], next: [], changed: true },
      { feature: 'impact', deployed: [ESTIMATOR, BOAVIZTA], next: [BOAVIZTA, ESTIMATOR], changed: true },
    ))

    const panels = wrapper.findAll('.precedence-panel')
    expect(panels.map(panel => panel.find('.precedence-panel-title').text())).toEqual(['Deployed', 'After the redeploy'])
    expect(wrapper.find('.precedence-arrow').exists()).toBe(true)
    expect(panels[0].findAll('.precedence-first').map(first => first.text())).toEqual(['kubernetes-metrics', 'estimator'])
    expect(panels[1].findAll('.precedence-row')[0].text()).toContain('None')
    expect(panels[1].find('.precedence-first').text()).toBe('boavizta')
    expect(panels[1].findAll('.precedence-row.changed')).toHaveLength(2)
  })

  it('shows the whole chain a component goes through, when hovering the module serving it first', async () => {
    const wrapper = render(precedence(false, { feature: 'impact', deployed: [ESTIMATOR, BOAVIZTA], next: [ESTIMATOR, BOAVIZTA], changed: false }))

    await wrapper.findAll('.precedence-row .pill-hint').find(hint => hint.find('.precedence-first').exists())!.trigger('mouseenter')

    const stages = [...document.querySelectorAll('.pill-hint-bubble .precedence-stage')].map(stage => stage.textContent)
    expect(stages).toEqual(['Asset\'s own module', 'estimator', 'boavizta'])
    expect(document.querySelectorAll('.pill-hint-bubble .precedence-chain-arrow')).toHaveLength(2)
    wrapper.unmount()
  })

  it('names a module it does not know by its full name', () => {
    const wrapper = render(precedence(false, { feature: 'impact', deployed: ['acme.unknown'], next: ['acme.unknown'], changed: false }))

    expect(wrapper.find('.precedence-first').text()).toBe('acme.unknown')
  })
})
