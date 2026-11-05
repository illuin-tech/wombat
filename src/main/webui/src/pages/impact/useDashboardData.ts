import { ref } from 'vue'
import type { EnvironmentInfo, LiveEnvironment } from '@/domain/environment'
import type { AssetImpact, ImpactRequest, TimelineResponse } from '@/domain/impact'
import { fetchEnvironments, fetchLiveEnvironments } from '@/services/api/environments'
import { fetchImpact, fetchTimeline } from '@/services/api/impact'
import { useAsync, type AsyncState } from '@/services/tools/useAsync'
import type { DashboardRequests } from '@/pages/impact/model/dashboard'

export interface DashboardApi {
  fetchLiveEnvironments: () => Promise<LiveEnvironment[]>
  fetchEnvironments: () => Promise<EnvironmentInfo[]>
  fetchImpact: (request: ImpactRequest) => Promise<AssetImpact[]>
  fetchTimeline: (request: ImpactRequest) => Promise<TimelineResponse>
}

export interface DashboardData {
  liveEnvironments: AsyncState<LiveEnvironment[], []>
  persistedEnvironments: AsyncState<EnvironmentInfo[], []>
  impact: AsyncState<AssetImpact[], [request: ImpactRequest]>
  timeline: AsyncState<TimelineResponse, [request: ImpactRequest]>
  impactEnvironmentId: ReturnType<typeof ref<string | null>>
  timelineEnvironmentId: ReturnType<typeof ref<string | null>>
  loadEnvironments: () => void
  loadEvaluation: (environmentId: string, requests: DashboardRequests) => void
}

const defaultApi: DashboardApi = {
  fetchLiveEnvironments,
  fetchEnvironments,
  fetchImpact,
  fetchTimeline,
}

export function useDashboardData(api: DashboardApi = defaultApi): DashboardData {
  const liveEnvironments = useAsync(api.fetchLiveEnvironments)
  const persistedEnvironments = useAsync(api.fetchEnvironments)
  const impact = useAsync(api.fetchImpact)
  const timeline = useAsync(api.fetchTimeline)
  const impactEnvironmentId = ref<string | null>(null)
  const timelineEnvironmentId = ref<string | null>(null)
  let evaluationGeneration = 0

  function loadEnvironments(): void {
    void persistedEnvironments.run()
    void liveEnvironments.run()
  }

  function loadEvaluation(environmentId: string, requests: DashboardRequests): void {
    const generation = ++evaluationGeneration
    void impact.run(requests.impact).then((result) => {
      if (generation === evaluationGeneration && result !== null) impactEnvironmentId.value = environmentId
    })
    void timeline.run(requests.timeline).then((result) => {
      if (generation === evaluationGeneration && result !== null) timelineEnvironmentId.value = environmentId
    })
  }

  return {
    liveEnvironments,
    persistedEnvironments,
    impact,
    timeline,
    impactEnvironmentId,
    timelineEnvironmentId,
    loadEnvironments,
    loadEvaluation,
  }
}
