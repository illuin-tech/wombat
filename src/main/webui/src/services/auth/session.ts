import { readonly, ref } from 'vue'
import { fetchAppInfo, type AppInfo } from '@/services/api/app'

const info = ref<AppInfo | null>(null)
let pending: Promise<AppInfo> | null = null

/** Loads GET /api/app once and shares it across the application. */
export function loadAppInfo(): Promise<AppInfo> {
  if (!pending) {
    pending = fetchAppInfo()
      .then(result => {
        info.value = result
        return result
      })
      .catch(error => {
        pending = null
        throw error
      })
  }
  return pending
}

export function useAppInfo() {
  return { appInfo: readonly(info), loadAppInfo }
}
