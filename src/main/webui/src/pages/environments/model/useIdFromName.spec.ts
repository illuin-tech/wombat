import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { effectScope, nextTick, ref, type EffectScope } from 'vue'
import { useIdFromName } from './useIdFromName'

describe('id from name', () => {
  let scope: EffectScope

  beforeEach(() => { scope = effectScope() })
  afterEach(() => scope.stop())

  function setup(following = true) {
    const name = ref('')
    const creating = ref(following)
    const derived = scope.run(() => useIdFromName(name, () => creating.value))!
    return { name, creating, ...derived }
  }

  it('follows the name until the id is edited', async () => {
    const { name, id, idEdited, onIdInput } = setup()
    name.value = 'My Lab'
    await nextTick()
    expect(id.value).toBe('my-lab')

    onIdInput({ target: { value: 'lab' } } as unknown as Event)
    name.value = 'My Other Lab'
    await nextTick()
    expect(id.value).toBe('lab')
    expect(idEdited.value).toBe(true)
  })

  it('follows the name again once reset', async () => {
    const { name, id, onIdInput, resetId } = setup()
    onIdInput({ target: { value: 'lab' } } as unknown as Event)
    resetId()
    name.value = 'Staging'
    await nextTick()
    expect(id.value).toBe('staging')
  })

  it('leaves the id alone when not following', async () => {
    const { name, id, resetId } = setup(false)
    resetId('lab')
    name.value = 'Laboratory'
    await nextTick()
    expect(id.value).toBe('lab')
  })
})
