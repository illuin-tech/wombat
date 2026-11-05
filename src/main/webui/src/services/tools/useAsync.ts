import { ref, shallowRef, type Ref } from 'vue'

export interface AsyncState<T, A extends unknown[]> {
  data: Ref<T | null>
  error: Ref<unknown>
  loading: Ref<boolean>
  run: (...args: A) => Promise<T | null>
}

/** Runs an async loader, keeping only the latest call's outcome. */
export function useAsync<T, A extends unknown[] = []>(loader: (...args: A) => Promise<T>): AsyncState<T, A> {
  const data = shallowRef<T | null>(null)
  const error = shallowRef<unknown>(null)
  const loading = ref(false)
  let generation = 0

  async function run(...args: A): Promise<T | null> {
    const current = ++generation
    loading.value = true
    error.value = null
    try {
      const result = await loader(...args)
      if (current === generation) data.value = result
      return result
    }
    catch (caught) {
      if (current === generation) {
        data.value = null
        error.value = caught
      }
      return null
    }
    finally {
      if (current === generation) loading.value = false
    }
  }

  return { data, error, loading, run }
}
