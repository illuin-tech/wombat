import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

/** A header dropdown (user menu, settings): toggled by its trigger, closed by an outside click or Escape. */
export function useDropdown(): { open: Ref<boolean>; root: Ref<HTMLElement | null> } {
  const open = ref(false)
  const root = ref<HTMLElement | null>(null)

  function onDocumentClick(event: MouseEvent): void {
    if (root.value && !root.value.contains(event.target as Node))
      open.value = false
  }

  function onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape')
      open.value = false
  }

  onMounted(() => {
    document.addEventListener('click', onDocumentClick)
    document.addEventListener('keydown', onKeydown)
  })
  onBeforeUnmount(() => {
    document.removeEventListener('click', onDocumentClick)
    document.removeEventListener('keydown', onKeydown)
  })

  return { open, root }
}
