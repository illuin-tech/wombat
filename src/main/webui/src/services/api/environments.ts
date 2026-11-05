import type { EnvironmentInfo, LiveEnvironment } from '@/domain/environment'
import { request } from '@/services/api/client'

export type { AssetInfo, EnvironmentInfo, LiveEnvironment } from '@/domain/environment'

/** The live context includes only environments and assets recognized by the evaluator. */
export function fetchLiveEnvironments(): Promise<LiveEnvironment[]> {
  return request<LiveEnvironment[]>('/api/impact/environments')
}

/** Persisted records are used only to identify assets absent from the live context. */
export function fetchEnvironments(): Promise<EnvironmentInfo[]> {
  return request<EnvironmentInfo[]>('/api/environments')
}
