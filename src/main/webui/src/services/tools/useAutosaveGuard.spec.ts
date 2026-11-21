import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref, type Ref } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import type { Autosave, AutosaveStatus } from './useAutosave'
import { autosaveStatusText, useAutosaveGuard, type AutosaveGuard } from './useAutosaveGuard'

/* An autosave whose pending changes the test decides, and whose flush saves them or not */
function fakeAutosave(unsaved: Ref<boolean>, saves: boolean, status: AutosaveStatus = 'idle', error: string | null = null): Autosave<unknown> {
  return {
    status: ref(status),
    error: ref(error),
    hasUnsavedChanges: () => unsaved.value,
    flush: vi.fn(async () => { if (saves) unsaved.value = false }),
    reset: () => {},
  }
}

let wrapper: VueWrapper | null = null
afterEach(() => wrapper?.unmount())

async function setup(autosave: Autosave<unknown>): Promise<{ router: Router; guard: () => AutosaveGuard }> {
  let guard!: AutosaveGuard
  const Page = defineComponent({ setup() { guard = useAutosaveGuard(autosave); return () => h('div') } })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/page', component: Page }, { path: '/elsewhere', component: { render: () => h('div') } }] })
  await router.push('/page')
  wrapper = mount({ template: '<RouterView />' }, { global: { plugins: [router] } })
  await flushPromises()
  return { router, guard: () => guard }
}

describe('autosaveStatusText', () => {
  it('tells where the autosave stands, with the reason of a failure', () => {
    expect(autosaveStatusText(fakeAutosave(ref(false), true))).toBe('')
    expect(autosaveStatusText(fakeAutosave(ref(false), true, 'saved'))).toBe('All changes saved')
    expect(autosaveStatusText(fakeAutosave(ref(false), true, 'error', 'Server down'))).toBe('Not saved: Server down')
  })
})

describe('useAutosaveGuard', () => {
  it('saves what is pending before leaving', async () => {
    const autosave = fakeAutosave(ref(true), true)
    const { router } = await setup(autosave)

    await router.push('/elsewhere')

    expect(autosave.flush).toHaveBeenCalledOnce()
    expect(router.currentRoute.value.path).toBe('/elsewhere')
  })

  it('asks before leaving changes that could not be saved, and stays unless told to leave', async () => {
    const { router, guard } = await setup(fakeAutosave(ref(true), false))

    const staying = router.push('/elsewhere')
    await flushPromises()
    expect(guard().leaving.value).toBe(true)
    guard().stay()
    await staying
    expect(router.currentRoute.value.path).toBe('/page')

    const leaving = router.push('/elsewhere')
    await flushPromises()
    guard().leave()
    await leaving
    expect(guard().leaving.value).toBe(false)
    expect(router.currentRoute.value.path).toBe('/elsewhere')
  })
})
