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
}

export interface AssetIdentity {
  id: string
  environmentId: string
  name: string
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

/** Server profiles are module records: only the ServerProfile members are relied upon, under either naming. */
export interface ServerProfile {
  provider?: string
  instanceType?: string
  'instance-type'?: string
  location?: string
  lifespan?: string
  [key: string]: unknown
}

export interface EcologitsMetric {
  type: string
  name: string
  value: { min: number; max: number }
  unit: string
}

export interface EcologitsEstimation {
  impacts: {
    energy: EcologitsMetric | null
    gwp: EcologitsMetric | null
    adpe: EcologitsMetric | null
    pe: EcologitsMetric | null
    [key: string]: unknown
  } | null
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
  estimation: EcologitsEstimation | null
  outputTokenCount: number
  requestCount: number
}

export type ServiceImpact = KubernetesImpact | LLMImpact

export interface TimeRangeJson {
  start: string
  end: string
  resolution?: string
}

export interface AssetImpact {
  assetIdentity: AssetIdentity
  footprint: Footprint
  serviceImpacts: ServiceImpact[]
  provider: 'BOAVIZTA' | 'ECOLOGITS'
  range: TimeRangeJson | null
}

export interface ImpactRequest {
  source_time_range?: { start: string; end: string }
  environments: { id: string; assets?: { id: string; service_ids?: string[] }[] }[]
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

export function isKubernetesImpact(impact: ServiceImpact): impact is KubernetesImpact {
  return 'loadPercent' in impact
}

export function isLLMImpact(impact: ServiceImpact): impact is LLMImpact {
  return 'requestCount' in impact
}
