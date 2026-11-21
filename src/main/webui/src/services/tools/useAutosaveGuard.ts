import { onBeforeUnmount, onMounted, readonly, ref, type Ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import type { Autosave, AutosaveStatus } from '@/services/tools/useAutosave'

const STATUS_TEXTS: Record<AutosaveStatus, string> = {
  idle: '',
  pending: 'Unsaved changes',
  saving: 'Saving…',
  saved: 'All changes saved',
  invalid: 'Not saved: fix the highlighted fields',
  error: 'Not saved',
}

/** Where an autosave stands, for the page's status line; empty when there is nothing to say. */
export function autosaveStatusText<T>(autosave: Autosave<T>): string {
  const text = STATUS_TEXTS[autosave.status.value]
  return autosave.status.value === 'error' && autosave.error.value ? `${text}: ${autosave.error.value}` : text
}

export interface AutosaveGuard {
  /** Whether leaving waits for the user: changes could not be saved. Shown as a `ConfirmDialog`. */
  leaving: Readonly<Ref<boolean>>
  leave: () => void
  stay: () => void
}

/**
 * Saves what is pending before the page is left, and asks before leaving changes that could not be saved; a closing
 * tab warns about them.
 *
 * @param active whether the guard applies, e.g. not while the page creates what it autosaves
 */
export function useAutosaveGuard<T>(autosave: Autosave<T>, active: () => boolean = () => true): AutosaveGuard {
  const leaving = ref(false)
  let answer: ((leave: boolean) => void) | null = null

  onBeforeRouteLeave(async () => {
    if (!active()) return true
    if (autosave.hasUnsavedChanges()) await autosave.flush()
    if (!autosave.hasUnsavedChanges()) return true
    leaving.value = true
    return new Promise<boolean>(resolve => { answer = resolve })
  })

  function settle(leave: boolean): void {
    leaving.value = false
    answer?.(leave)
    answer = null
  }

  function onBeforeUnload(event: BeforeUnloadEvent): void {
    if (active() && autosave.hasUnsavedChanges()) event.preventDefault()
  }

  onMounted(() => window.addEventListener('beforeunload', onBeforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload))

  return { leaving: readonly(leaving), leave: () => settle(true), stay: () => settle(false) }
}
