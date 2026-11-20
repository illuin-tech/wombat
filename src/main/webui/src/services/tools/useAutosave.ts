import { readonly, ref, watch, type Ref } from 'vue'
import { useDebounceFn } from '@vueuse/core'
import { errorText } from '@/services/api/client'

export type AutosaveStatus = 'idle' | 'pending' | 'saving' | 'saved' | 'invalid' | 'error'

export interface AutosaveOptions<T> {
  /** Idleness after the last change before saving, in milliseconds. */
  delay?: number
  /** Changes that would be refused are not sent, and wait for the next change. */
  isValid?: (value: T) => boolean
}

export interface Autosave<T> {
  status: Readonly<Ref<AutosaveStatus>>
  error: Readonly<Ref<string | null>>
  /** Whether a change is not saved yet. */
  hasUnsavedChanges: () => boolean
  /** Saves right away what is pending, e.g. before leaving the page. */
  flush: () => Promise<void>
  /** Takes the value as saved, e.g. once loaded from the server. */
  reset: (value: T) => void
}

/**
 * Saves the value a while after it stops changing. One save runs at a time: changes made meanwhile are saved once it
 * completes, and a failed save is retried on the next change.
 */
export function useAutosave<T>(source: () => T, save: (value: T) => Promise<void>, options: AutosaveOptions<T> = {}): Autosave<T> {
  const status = ref<AutosaveStatus>('idle')
  const error = ref<string | null>(null)
  const isValid = options.isValid ?? (() => true)
  let saved: string | null = null
  let inFlight: Promise<void> | null = null

  const snapshot = (value: T) => JSON.stringify(value)
  const hasUnsavedChanges = () => saved !== null && snapshot(source()) !== saved

  async function run(): Promise<void> {
    if (inFlight) {
      await inFlight
      return run()
    }
    const value = source()
    const current = snapshot(value)
    if (saved === null || current === saved) {
      if (status.value === 'pending') status.value = 'saved'
      return
    }
    if (!isValid(value)) {
      status.value = 'invalid'
      return
    }

    status.value = 'saving'
    error.value = null
    let changedMeanwhile = false
    inFlight = save(JSON.parse(current) as T)
      .then(() => {
        saved = current
        changedMeanwhile = hasUnsavedChanges()
        status.value = changedMeanwhile ? 'pending' : 'saved'
      })
      .catch(caught => {
        status.value = 'error'
        error.value = errorText(caught)
      })
      .finally(() => {
        inFlight = null
      })
    await inFlight
    if (changedMeanwhile) await run()
  }

  const debounced = useDebounceFn(run, options.delay ?? 1500)

  watch(source, value => {
    if (saved === null || snapshot(value) === saved) return
    status.value = isValid(value) ? 'pending' : 'invalid'
    void debounced()
  }, { deep: true })

  return {
    status: readonly(status),
    error: readonly(error),
    hasUnsavedChanges,
    flush: run,
    reset(value: T) {
      saved = snapshot(value)
      status.value = 'idle'
      error.value = null
    },
  }
}
