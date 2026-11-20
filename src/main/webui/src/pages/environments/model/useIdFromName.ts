import { ref, watch, type Ref, type WatchSource } from 'vue'
import { slugify } from '@/pages/environments/model/identifiers'

export interface IdFromName {
  id: Ref<string>
  /** Whether the id was typed, which stops it from following the name. */
  idEdited: Ref<boolean>
  /** The id field's input handler. */
  onIdInput: (event: Event) => void
  /** Starts over with the id, following the name again. */
  resetId: (id?: string) => void
}

/** An id derived from the name while `following` holds, e.g. during a creation, until it is edited. */
export function useIdFromName(name: WatchSource<string>, following: () => boolean): IdFromName {
  const id = ref('')
  const idEdited = ref(false)

  watch(name, value => {
    if (following() && !idEdited.value) id.value = slugify(value)
  })

  return {
    id,
    idEdited,
    onIdInput(event: Event) {
      idEdited.value = true
      id.value = (event.target as HTMLInputElement).value
    },
    resetId(value = '') {
      id.value = value
      idEdited.value = false
    },
  }
}
