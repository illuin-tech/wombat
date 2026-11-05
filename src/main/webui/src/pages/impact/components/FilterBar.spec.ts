import {afterEach, describe, expect, it} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {createMemoryHistory, createRouter} from 'vue-router'
import FilterBar from '@/pages/impact/components/FilterBar.vue'
import type {AssetInfo, LiveEnvironment} from '@/domain/environment'

const assetA: AssetInfo = {id: 'a', name: 'Asset A', namespace: 'one', type: 'container'}
const assetB: AssetInfo = {id: 'b', name: 'Asset B', namespace: 'two', type: 'container'}
const environments: LiveEnvironment[] = [
  {id: 'env', name: 'Environment', assets: [assetA, assetB]},
  {id: 'next', name: 'Next environment', assets: [assetA]},
]
const from = '2026-10-01T00:00'
const to = '2026-11-01T00:00'

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{path: '/impact/:environment', name: 'impact', component: {template: '<div />'}}],
  })
}

async function mountFilter(selection = new Map<string, string[]>()) {
  const router = makeRouter()
  await router.push({name: 'impact', params: {environment: 'env'}, query: {from, to}})
  await router.isReady()
  const wrapper = mount(FilterBar, {
    props: {environments, environmentId: 'env', assets: [assetA, assetB], services: ['api', 'worker'], selection, from, to, maxSpanMs: 120 * 86_400_000},
    global: {plugins: [router]},
  })
  return {router, wrapper}
}

describe('FilterBar routing', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('navigates immediately with dates only when the environment changes', async () => {
    const {router, wrapper} = await mountFilter()
    wrapper.element.querySelector('#envTrigger')?.dispatchEvent(new MouseEvent('click', {bubbles: true}))
    const radio = wrapper.get('input[name="environment"][value="next"]')
    ;(radio.element as HTMLInputElement).checked = true
    await radio.trigger('change')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/impact/next')
    expect(router.currentRoute.value.query).toEqual({from, to})
    wrapper.unmount()
  })

  it('navigates immediately for checked assets and applies global service IDs to each selected asset', async () => {
    const {router, wrapper} = await mountFilter(new Map([['a', []], ['b', []]]))
    const asset = wrapper.get('input[name="assets"][value="b"]')
    ;(asset.element as HTMLInputElement).checked = false
    await asset.trigger('change')
    await flushPromises()
    expect(router.currentRoute.value.query.services).toEqual(['a'])

    const secondAsset = wrapper.get('input[name="assets"][value="b"]')
    ;(secondAsset.element as HTMLInputElement).checked = true
    await secondAsset.trigger('change')
    await flushPromises()
    expect(router.currentRoute.value.query.services).toEqual(['a', 'b'])

    await wrapper.get('#serviceTrigger').trigger('click')
    const worker = wrapper.get('input[name="containers"][value="worker"]')
    ;(worker.element as HTMLInputElement).checked = false
    await worker.trigger('change')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.query.services).toEqual(['a=api', 'b=api'])
    expect(router.currentRoute.value.query.from).toBe(from)
    expect(router.currentRoute.value.query.to).toBe(to)
    wrapper.unmount()
  })
})
