import type { AssetImpact, ImpactRequest, TimelineResponse } from '@/domain/impact'
import { request } from '@/services/api/client'

export type { AssetImpact, ImpactRequest, TimelineResponse } from '@/domain/impact'

export function fetchImpact(body: ImpactRequest): Promise<AssetImpact[]> {
  return request<AssetImpact[]>('/api/impact', { method: 'POST', body })
}

export function fetchTimeline(body: ImpactRequest): Promise<TimelineResponse> {
  return request<TimelineResponse>('/api/impact/timeline', { method: 'POST', body })
}
