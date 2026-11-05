import type { AssetImpact, ImpactRequest, TimelineResponse } from '@/domain/impact'
import { request } from '@/services/api/client'

export type { AssetImpact, ImpactRequest, TimelineResponse } from '@/domain/impact'

/** `status.metadata.reason` of the 400 sent when no asset recorded activity in the requested range. */
export const NO_DATA_REASON = 'no-data'

export function fetchImpact(body: ImpactRequest): Promise<AssetImpact[]> {
  return request<AssetImpact[]>('/api/impact', { method: 'POST', body })
}

export function fetchTimeline(body: ImpactRequest): Promise<TimelineResponse> {
  return request<TimelineResponse>('/api/impact/timeline', { method: 'POST', body })
}
