export type ServiceFamily = 'UNKNOWN' | 'KUBERNETES_CONTAINER' | 'LLM'
export type ActivityRegime = 'UNKNOWN' | 'MEASURED' | 'MODELED'

export interface AssetType {
  namespace: string
  localName: string
  name: string
  regime: ActivityRegime
  family: ServiceFamily
}

export interface FootprintImpactItem {
  value: number
  warnings?: string[]
}

export interface FootprintImpact {
  unit: string
  description: string
  embedded: FootprintImpactItem
  use: FootprintImpactItem
}

export interface Footprint {
  gwp: FootprintImpact | null
  pe: FootprintImpact | null
  adp: FootprintImpact | null
  energy: FootprintImpact | null
}

export interface AssetIdentity {
  id: string
  environmentId: string
  /** The asset's type name. */
  type: string
}

export interface ClusterInfo {
  id: string
  namespace: string
}

export interface ServerSpecification {
  vcpu: number | null
  memory: number | null
  ssdStorage: number | null
  hddStorage: number | null
  gpuUnits: number | null
  platform: string | null
}

/** Server profiles are module records and may contain module-specific properties. */
export interface ServerProfile {
  provider?: string
  instanceType?: string
  location?: string
  lifespan?: string
  [key: string]: unknown
}

export interface BaseServiceImpact {
  serviceId: string
  assetType: AssetType
  footprint: Footprint
  profile: unknown
}

export interface KubernetesImpact extends BaseServiceImpact {
  profile: ServerProfile | null
  share: number
  location: ClusterInfo | null
  serverSpecification: ServerSpecification | null
  loadPercent: number
  nodeCount: number
}

export interface LLMImpact extends BaseServiceImpact {
  provider: string | null
  model: string | null
  location: string | null
  outputTokenCount: number
  requestCount: number
}

export type ServiceImpact = KubernetesImpact | LLMImpact

export interface TimeRangeJson {
  start: string
  end: string
  resolution?: string
}

/** The origins this UI knows how to describe: declared by the user, or inferred by the kubernetes-api module. */
export type KnownProfileOrigin = 'DECLARED' | 'NODES' | 'RESOURCE_QUOTAS' | 'CONTAINER_REQUESTS' | 'CONTAINER_LIMITS'

/**
 * Where the profile an asset was evaluated with comes from. Each module defines the origins of the profiles it infers,
 * so values beyond the known ones may come.
 */
export type ProfileOrigin = KnownProfileOrigin | (string & {})

export interface AssetImpact {
  assetIdentity: AssetIdentity
  footprint: Footprint
  serviceImpacts: ServiceImpact[]
  provider: 'BOAVIZTA' | 'ECOLOGITS'
  range: TimeRangeJson | null
  profileOrigin: ProfileOrigin
  /** What the evaluation had to assume, e.g. to infer the asset's profile; empty when nothing. */
  warnings: string[]
}

/** `status.metadata.failures` of the impact responses: an asset left out of them because its evaluation failed. */
export interface AssetEvaluationFailure {
  assetIdentity: AssetIdentity
  reason: string
}

/** The impacts of the assets evaluated, and the assets that could not be. */
export interface ImpactResult {
  impacts: AssetImpact[]
  failures: AssetEvaluationFailure[]
}

export interface ImpactRequest {
  sourceTimeRange?: { start: string; end: string }
  environments: { id: string; assets?: { id: string; serviceIds?: string[] }[] }[]
}

export interface TimelineBucket {
  embedded: number
  use: number
}

export interface TimelineSeries {
  key: 'gwp' | 'pe' | 'adp'
  label: string
  unit: string
  buckets: TimelineBucket[]
}

export interface TimelineResponse {
  stepMs: number
  stepLabel: string
  stepPlural: string
  bucketStarts: number[]
  series: TimelineSeries[]
}

/** `status.metadata.failures` of the impact responses, both successful and no-data ones; malformed entries are left out. */
export function evaluationFailures(metadata: Record<string, unknown>): AssetEvaluationFailure[] {
  const failures = metadata.failures
  if (!Array.isArray(failures)) return []
  return failures.filter((failure): failure is AssetEvaluationFailure => {
    if (!failure || typeof failure !== 'object') return false
    const { assetIdentity, reason } = failure as Record<string, unknown>
    return typeof reason === 'string'
      && !!assetIdentity && typeof assetIdentity === 'object'
      && typeof (assetIdentity as Record<string, unknown>).id === 'string'
  })
}

export function isKubernetesImpact(impact: ServiceImpact): impact is KubernetesImpact {
  return 'loadPercent' in impact
}

export function isLLMImpact(impact: ServiceImpact): impact is LLMImpact {
  return 'requestCount' in impact
}
