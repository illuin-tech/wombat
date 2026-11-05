import type { AppInfo } from '@/domain/app'
import { request } from '@/services/api/client'

export type { AppInfo, AppUser } from '@/domain/app'

export function fetchAppInfo(): Promise<AppInfo> {
  return request<AppInfo>('/api/app')
}
