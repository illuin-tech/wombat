import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, reactive, type EffectScope } from 'vue'
import { useAutosave } from './useAutosave'

interface Fields { name: string }

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

describe('autosave', () => {
  let scope: EffectScope

  beforeEach(() => {
    scope = effectScope()
    vi.useFakeTimers()
  })
  afterEach(() => {
    scope.stop()
    vi.useRealTimers()
  })

  function setup(save: (value: Fields) => Promise<void>) {
    const fields = reactive<Fields>({ name: 'Lab' })
    const autosave = scope.run(() => useAutosave(() => ({ ...fields }), save, { delay: 1000, isValid: value => value.name.trim() !== '' }))!
    autosave.reset({ ...fields })
    return { fields, autosave }
  }

  it('saves once the value stops changing', async () => {
    const save = vi.fn(async () => {})
    const { fields, autosave } = setup(save)

    fields.name = 'Lab 1'
    await nextTick()
    expect(autosave.status.value).toBe('pending')
    await vi.advanceTimersByTimeAsync(500)
    fields.name = 'Lab 2'
    await nextTick()
    await vi.advanceTimersByTimeAsync(999)
    expect(save).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)
    expect(save).toHaveBeenCalledExactlyOnceWith({ name: 'Lab 2' })
    expect(autosave.status.value).toBe('saved')
    expect(autosave.hasUnsavedChanges()).toBe(false)
  })

  it('does not save what was loaded, nor invalid values', async () => {
    const save = vi.fn(async () => {})
    const { fields, autosave } = setup(save)

    fields.name = 'Lab'
    await nextTick()
    fields.name = ' '
    await nextTick()
    await vi.advanceTimersByTimeAsync(2000)

    expect(save).not.toHaveBeenCalled()
    expect(autosave.status.value).toBe('invalid')
  })

  it('saves the changes made during a save once it completes', async () => {
    const first = deferred<void>()
    const save = vi.fn().mockReturnValueOnce(first.promise).mockResolvedValue(undefined)
    const { fields, autosave } = setup(save)

    fields.name = 'Lab 1'
    await nextTick()
    await vi.advanceTimersByTimeAsync(1000)
    expect(autosave.status.value).toBe('saving')

    fields.name = 'Lab 2'
    await nextTick()
    first.resolve()
    await vi.advanceTimersByTimeAsync(1000)

    expect(save.mock.calls.map(call => call[0])).toEqual([{ name: 'Lab 1' }, { name: 'Lab 2' }])
    expect(autosave.status.value).toBe('saved')
  })

  it('reports a failed save, and retries on the next change', async () => {
    const save = vi.fn().mockRejectedValueOnce(new Error('Server down')).mockResolvedValue(undefined)
    const { fields, autosave } = setup(save)

    fields.name = 'Lab 1'
    await nextTick()
    await vi.advanceTimersByTimeAsync(1000)
    expect(autosave.status.value).toBe('error')
    expect(autosave.error.value).toBe('Server down')
    expect(autosave.hasUnsavedChanges()).toBe(true)

    fields.name = 'Lab 2'
    await nextTick()
    await vi.advanceTimersByTimeAsync(1000)
    expect(autosave.status.value).toBe('saved')
    expect(autosave.error.value).toBeNull()
  })

  it('saves right away when flushed', async () => {
    const save = vi.fn(async () => {})
    const { fields, autosave } = setup(save)

    fields.name = 'Lab 1'
    await nextTick()
    await autosave.flush()

    expect(save).toHaveBeenCalledExactlyOnceWith({ name: 'Lab 1' })
  })
})
