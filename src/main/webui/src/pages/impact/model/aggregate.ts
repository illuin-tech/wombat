/* Port of tech.illuin.wombat.ui.EnvironmentImpact: folds the raw per-asset impacts into the dashboard model. */
import {
  type AssetImpact,
  type ClusterInfo,
  type Footprint,
  type FootprintImpact,
  type KubernetesImpact,
  type LLMImpact,
  type ProfileOrigin,
  type ServerSpecification,
  type ServiceImpact as RawServiceImpact,
  type TimeRangeJson,
  isKubernetesImpact,
  isLLMImpact,
} from '@/domain/impact'
import type { ActivityRegime, AssetInfo, AssetType, ServiceFamily } from '@/domain/environment'

/** AmountUnit symbols. */
export const UNIT = { mj: 'MJ', kwh: 'kWh', kgCo2eq: 'kgCO2eq', kgSbeq: 'kgSbeq' } as const
const KNOWN_UNITS = new Set<string>(Object.values(UNIT))

export interface Amount {
  value: number
  unit: string
}

export interface ServiceImpact {
  assetName: string
  service: string
  label: string
  assetType: AssetType
  footprint: Footprint
}

export interface ServiceLine {
  service: string
  regime: ActivityRegime
  family: ServiceFamily
  gwp: Amount
  energy: Amount
  pe: Amount
  adpe: Amount
}

interface PanelBase {
  name: string
  type: AssetType
  regime: ActivityRegime
  serviceIds: string[]
  gwpTotal: Amount
  energyTotal: Amount
  peTotal: Amount
  adpeTotal: Amount
}

export interface KubernetesPanel extends PanelBase {
  kind: 'kubernetes'
  /** Null for a bare server profile, which has neither provider nor instance type. */
  provider: string | null
  instanceType: string | null
  location: string | null
  lifespan: number
  profileOrigin: ProfileOrigin
  /** What the evaluation of the asset had to assume, e.g. to infer its profile. */
  warnings: string[]
  clusters: ClusterInfo[]
  specification: ServerSpecification | null
  load: { cpuUsageCores: number; loadPercent: number; nodeCount: number }
}

export interface LLMPanel extends PanelBase {
  kind: 'llm'
  gwpPerRequest: Amount
  provider: string | null
  model: string
  location: string
  outputTokenCount: number
  /** The period's requests scaled to a 365-day year, 0 when the period is unknown. */
  requestPerYear: number
  requestCount: number
}

export type AssetPanel = KubernetesPanel | LLMPanel

export interface DashboardModel {
  globalImpact: Footprint
  dockerImpact: Footprint
  llmImpact: Footprint
  serviceImpacts: ServiceImpact[]
  impactShares: Map<string, number>
  allServices: string[]
  includedServices: string[]
  assetPanels: AssetPanel[]
  kubernetesPanels: KubernetesPanel[]
  llmPanels: LLMPanel[]
  serviceLines: ServiceLine[]
  assetCount: number
  serviceCount: number
  dockerServiceCount: number
  llmServiceCount: number
}

export function totalValue(impact: FootprintImpact | null | undefined): number {
  if (!impact) return 0
  return (impact.embedded?.value ?? 0) + (impact.use?.value ?? 0)
}

function zero(unit: string): FootprintImpact {
  return { unit, description: '', embedded: { value: 0, warnings: [] }, use: { value: 0, warnings: [] } }
}

function sumImpact(impacts: (FootprintImpact | null | undefined)[]): FootprintImpact | null {
  const present = impacts.filter((impact): impact is FootprintImpact => !!impact)
  if (present.length === 0) return null
  let embedded = 0
  let use = 0
  for (const impact of present) {
    embedded += impact.embedded?.value ?? 0
    use += impact.use?.value ?? 0
  }
  return {
    unit: present[0].unit,
    description: present[0].description,
    embedded: { value: embedded, warnings: [] },
    use: { value: use, warnings: [] },
  }
}

export function sumFootprints(footprints: Footprint[]): Footprint {
  if (footprints.length === 0)
    return { gwp: zero(UNIT.kgCo2eq), pe: zero(UNIT.mj), adp: zero(UNIT.kgSbeq), energy: zero(UNIT.kwh) }
  return {
    gwp: sumImpact(footprints.map(footprint => footprint.gwp)),
    pe: sumImpact(footprints.map(footprint => footprint.pe)),
    adp: sumImpact(footprints.map(footprint => footprint.adp)),
    energy: sumImpact(footprints.map(footprint => footprint.energy)),
  }
}

function unitOrFallback(symbol: string | null | undefined, fallback: string): string {
  return symbol && KNOWN_UNITS.has(symbol) ? symbol : fallback
}

function footprintAmount(impact: FootprintImpact | null | undefined, fallback: string): Amount {
  if (!impact) return { value: 0, unit: fallback }
  return { value: totalValue(impact), unit: unitOrFallback(impact.unit, fallback) }
}

const HOURS_PER_DAY = 24
const HOURS_PER_YEAR = 365 * HOURS_PER_DAY
const HOURS_PER_MONTH = Math.trunc(HOURS_PER_YEAR / 12)
const MS_PER_HOUR = 3_600_000

/** Lifetime.asHours() from its JSON text ("5y", "43800", "30d"…). */
export function lifetimeHours(lifespan: unknown): number {
  if (typeof lifespan === 'number') return lifespan
  if (typeof lifespan !== 'string') return 0
  const match = /^(\d+)(?:\s*([dmyh]))?$/i.exec(lifespan.trim())
  if (!match) return 0
  const value = Number(match[1])
  switch ((match[2] ?? 'h').toLowerCase()) {
    case 'd': return value * HOURS_PER_DAY
    case 'm': return value * HOURS_PER_MONTH
    case 'y': return value * HOURS_PER_YEAR
    default: return value
  }
}

/** A service's line in the model, under its asset's name. */
export function serviceImpact(assetName: string, impact: Pick<RawServiceImpact, 'serviceId' | 'assetType' | 'footprint'>): ServiceImpact {
  return {
    assetName,
    service: impact.serviceId,
    label: `${assetName} / ${impact.serviceId}`,
    assetType: impact.assetType,
    footprint: impact.footprint,
  }
}

/**
 * `impacts` is expected to be unfiltered: the full set of services feeds the service picker, and
 * `requestedServices` narrows what the cards, panels and table account for. Empty means every service.
 */
export function aggregate(impacts: AssetImpact[], assets: AssetInfo[], requestedServices: string[] = []): DashboardModel {
  const assetNames = new Map<string, string>()
  for (const asset of assets)
    if (!assetNames.has(asset.id)) assetNames.set(asset.id, asset.name ?? asset.id)

  const allServices = [...new Set(impacts.flatMap(impact => impact.serviceImpacts.map(service => service.serviceId)))].sort()
  const included = new Set(requestedServices.length === 0 ? allServices : allServices.filter(service => requestedServices.includes(service)))

  const panels: AssetPanel[] = []
  const services: ServiceImpact[] = []
  const dockerFootprints: Footprint[] = []
  const llmFootprints: Footprint[] = []

  for (const assetImpact of impacts) {
    const name = assetNames.get(assetImpact.assetIdentity.id) ?? assetImpact.assetIdentity.id
    const includedImpacts = assetImpact.serviceImpacts.filter(impact => included.has(impact.serviceId))
    if (includedImpacts.length === 0) continue

    includedImpacts.forEach(impact => services.push(serviceImpact(name, impact)))
    const assetFootprint = sumFootprints(includedImpacts.map(impact => impact.footprint))

    const kubernetesImpacts = includedImpacts.filter(isKubernetesImpact)
    if (kubernetesImpacts.length > 0) {
      dockerFootprints.push(assetFootprint)
      panels.push(kubernetesPanel(name, assetImpact, assetFootprint, kubernetesImpacts))
    }

    const llmImpacts = includedImpacts.filter(isLLMImpact)
    if (llmImpacts.length > 0) {
      llmFootprints.push(assetFootprint)
      panels.push(llmPanel(name, assetFootprint, llmImpacts, assetImpact.range))
    }
  }

  return {
    ...summarize(services, dockerFootprints, llmFootprints),
    allServices,
    includedServices: [...included],
    assetPanels: panels,
    kubernetesPanels: panels.filter((panel): panel is KubernetesPanel => panel.kind === 'kubernetes'),
    llmPanels: panels.filter((panel): panel is LLMPanel => panel.kind === 'llm'),
    assetCount: panels.length,
  }
}

/**
 * The model of footprints per service alone, without asset panels: what a simulation's results give, as the server
 * scaled them. Each service counts towards Docker or LLM by its family.
 */
export function serviceModel(services: ServiceImpact[], assetCount: number): DashboardModel {
  const footprints = (family: ServiceFamily) => services.filter(service => service.assetType.family === family).map(service => service.footprint)
  const allServices = [...new Set(services.map(service => service.service))].sort()
  return {
    ...summarize(services, footprints('KUBERNETES_CONTAINER'), footprints('LLM')),
    allServices,
    includedServices: allServices,
    assetPanels: [],
    kubernetesPanels: [],
    llmPanels: [],
    assetCount,
  }
}

/* What the cards, the shares and the service table read */
function summarize(services: ServiceImpact[], dockerFootprints: Footprint[], llmFootprints: Footprint[]) {
  const sorted = [...services].sort((a, b) => totalValue(b.footprint.gwp) - totalValue(a.footprint.gwp))
  const totalGwp = sorted.reduce((sum, service) => sum + totalValue(service.footprint.gwp), 0)
  const shares = new Map<string, number>()
  sorted.forEach(service => shares.set(service.label, totalGwp === 0 ? 0 : totalValue(service.footprint.gwp) / totalGwp))

  return {
    globalImpact: sumFootprints([...dockerFootprints, ...llmFootprints]),
    dockerImpact: sumFootprints(dockerFootprints),
    llmImpact: sumFootprints(llmFootprints),
    serviceImpacts: sorted,
    impactShares: shares,
    serviceLines: sorted.map(serviceLine),
    serviceCount: sorted.length,
    dockerServiceCount: sorted.filter(service => service.assetType.family === 'KUBERNETES_CONTAINER').length,
    llmServiceCount: sorted.filter(service => service.assetType.family === 'LLM').length,
  }
}

function kubernetesPanel(name: string, asset: Pick<AssetImpact, 'profileOrigin' | 'warnings'>, footprint: Footprint, impacts: KubernetesImpact[]): KubernetesPanel {
  const first = impacts[0]
  const profile = first.profile ?? {}
  const specification = first.serverSpecification
  const clusters: ClusterInfo[] = []
  for (const impact of impacts) {
    const location = impact.location
    if (location && !clusters.some(cluster => cluster.id === location.id && cluster.namespace === location.namespace))
      clusters.push(location)
  }

  return {
    kind: 'kubernetes',
    name,
    type: first.assetType,
    regime: first.assetType.regime,
    serviceIds: impacts.map(impact => impact.serviceId),
    gwpTotal: footprintAmount(footprint.gwp, UNIT.kgCo2eq),
    energyTotal: footprintAmount(footprint.energy, UNIT.kwh),
    peTotal: footprintAmount(footprint.pe, UNIT.mj),
    adpeTotal: footprintAmount(footprint.adp, UNIT.kgSbeq),
    provider: (profile.provider as string | undefined) ?? null,
    instanceType: profile.instanceType ?? null,
    location: (profile.location as string | undefined) ?? null,
    lifespan: lifetimeHours(profile.lifespan),
    // Impacts served before profiles could be inferred have neither
    profileOrigin: asset.profileOrigin ?? 'DECLARED',
    warnings: asset.warnings ?? [],
    clusters,
    specification,
    load: { cpuUsageCores: cpuUsageCores(first, specification), loadPercent: first.loadPercent, nodeCount: first.nodeCount },
  }
}

/** The load formula is `loadPercent = cores / vcpu * 100`, so the cores are recovered by inverting it. */
function cpuUsageCores(impact: KubernetesImpact, specification: ServerSpecification | null): number {
  const vcpu = specification?.vcpu ?? 0
  return vcpu > 0 ? impact.loadPercent / 100 * vcpu : 0
}

function distinct<T>(values: (T | null | undefined)[]): T[] {
  return [...new Set(values.filter((value): value is T => value !== null && value !== undefined))]
}

function llmPanel(name: string, footprint: Footprint, impacts: LLMImpact[], range: TimeRangeJson | null): LLMPanel {
  const first = impacts[0]
  const outputTokenCount = impacts.reduce((sum, impact) => sum + impact.outputTokenCount, 0)
  const requestCount = impacts.reduce((sum, impact) => sum + impact.requestCount, 0)
  const requestPerYear = perYear(requestCount, range)

  const providers = distinct(impacts.map(impact => impact.provider))
  return {
    kind: 'llm',
    name,
    type: first.assetType,
    regime: first.assetType.regime,
    serviceIds: impacts.map(impact => impact.serviceId),
    gwpTotal: footprintAmount(footprint.gwp, UNIT.kgCo2eq),
    energyTotal: footprintAmount(footprint.energy, UNIT.kwh),
    peTotal: footprintAmount(footprint.pe, UNIT.mj),
    adpeTotal: footprintAmount(footprint.adp, UNIT.kgSbeq),
    gwpPerRequest: { value: requestCount > 0 ? totalValue(footprint.gwp) / requestCount : 0, unit: UNIT.kgCo2eq },
    provider: providers.length === 1 ? first.provider : null,
    model: distinct(impacts.map(impact => impact.model)).join(', '),
    location: distinct(impacts.map(impact => impact.location)).join(', '),
    outputTokenCount,
    requestPerYear,
    requestCount,
  }
}

/* A NaN from a missing or malformed bound fails the `> 0` test like an empty period does */
function perYear(count: number, range: TimeRangeJson | null): number {
  const hours = range ? (Date.parse(range.end) - Date.parse(range.start)) / MS_PER_HOUR : 0
  return hours > 0 ? Math.round(count * HOURS_PER_YEAR / hours) : 0
}

function serviceLine(service: ServiceImpact): ServiceLine {
  return {
    service: service.service,
    regime: service.assetType.regime,
    family: service.assetType.family,
    gwp: footprintAmount(service.footprint.gwp, UNIT.kgCo2eq),
    energy: footprintAmount(service.footprint.energy, UNIT.kwh),
    pe: footprintAmount(service.footprint.pe, UNIT.mj),
    adpe: footprintAmount(service.footprint.adp, UNIT.kgSbeq),
  }
}
