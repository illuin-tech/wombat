import {
  type ClusterInfo,
  type KubernetesImpact,
  type ServerSpecification,
  type ServiceImpact,
  type TimeRangeJson,
  isKubernetesImpact,
  isLLMImpact,
} from '@/domain/impact'

const MS_PER_HOUR = 3_600_000
const HOURS_PER_DAY = 24

/** What an LLM service did over the period. The means are null when they cannot be told. */
export interface LlmServiceActivity {
  kind: 'llm'
  provider: string | null
  model: string | null
  location: string | null
  outputTokenCount: number
  outputTokensPerDay: number | null
  requestCount: number
  requestsPerDay: number | null
  outputTokensPerRequest: number | null
}

/** What a Kubernetes service did over the period: its part of its asset's CPU time. */
export interface KubernetesServiceActivity {
  kind: 'kubernetes'
  location: ClusterInfo | null
  cpuShare: number
  /** Null when the asset's vCPU is unknown. */
  cpuUsageCores: number | null
}

export type ServiceActivity = LlmServiceActivity | KubernetesServiceActivity

/** The length of the period in hours, 0 when it is unknown. NaN for a malformed bound, which fails a `> 0` test alike. */
export function rangeHours(range: TimeRangeJson | null): number {
  return range ? (Date.parse(range.end) - Date.parse(range.start)) / MS_PER_HOUR : 0
}

/** The load formula is `loadPercent = cores / vcpu * 100`, so the cores are recovered by inverting it. */
export function cpuUsageCores(impact: KubernetesImpact, specification: ServerSpecification | null): number {
  const vcpu = specification?.vcpu ?? 0
  return vcpu > 0 ? impact.loadPercent / 100 * vcpu : 0
}

/** The activity of a service of an asset evaluated over `range`, null for a family without one. */
export function serviceActivity(impact: ServiceImpact, range: TimeRangeJson | null): ServiceActivity | null {
  if (isKubernetesImpact(impact)) {
    const known = (impact.serverSpecification?.vcpu ?? 0) > 0
    return {
      kind: 'kubernetes',
      location: impact.location,
      cpuShare: impact.share,
      cpuUsageCores: known ? impact.share * cpuUsageCores(impact, impact.serverSpecification) : null,
    }
  }
  if (isLLMImpact(impact)) {
    const days = rangeHours(range) / HOURS_PER_DAY
    const perDay = (count: number) => days > 0 ? count / days : null
    return {
      kind: 'llm',
      provider: impact.provider,
      model: impact.model,
      location: impact.location,
      outputTokenCount: impact.outputTokenCount,
      outputTokensPerDay: perDay(impact.outputTokenCount),
      requestCount: impact.requestCount,
      requestsPerDay: perDay(impact.requestCount),
      outputTokensPerRequest: impact.requestCount > 0 ? impact.outputTokenCount / impact.requestCount : null,
    }
  }
  return null
}
