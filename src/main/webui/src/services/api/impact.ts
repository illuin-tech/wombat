import { evaluationFailures, type ImpactRequest, type ImpactResult, type TimelineResponse } from '@/domain/impact'
import { request, requestResponse, statusMetadata } from '@/services/api/client'

export type { AssetImpact, AssetEvaluationFailure, ImpactRequest, ImpactResult, TimelineResponse } from '@/domain/impact'

/** `status.metadata.reason` of the 400 sent when no asset recorded activity in the requested range. */
export const NO_DATA_REASON = 'no-data'

export async function fetchImpact(body: ImpactRequest): Promise<ImpactResult> {
  const response = await requestResponse<ImpactResult['impacts']>('/api/impact', { method: 'POST', body })
  return { impacts: response.payload, failures: evaluationFailures(statusMetadata(response.status)) }
}

/* The timeline's failures are those of the impact, which it is requested along with */
export function fetchTimeline(body: ImpactRequest): Promise<TimelineResponse> {
  return request<TimelineResponse>('/api/impact/timeline', { method: 'POST', body })
}
