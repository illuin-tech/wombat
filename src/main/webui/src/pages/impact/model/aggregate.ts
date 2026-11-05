/* Port of tech.illuin.wombat.ui.EnvironmentImpact: folds the raw per-asset impacts into the dashboard model. */
import {
  type AssetImpact,
  type ClusterInfo,
  type EcologitsMetric,
  type Footprint,
  type FootprintImpact,
  type KubernetesImpact,
  type LLMImpact,
  type ServerSpecification,
  type ServiceImpact as RawServiceImpact,
  isKubernetesImpact,
  isLLMImpact,
} from '@/domain/impact'
import type { ActivityRegime, AssetInfo, AssetType, ServiceFamily } from '@/domain/environment'

/** AmountUnit symbols. */
export const UNIT = { mj: 'MJ', kwh: 'KWh', kgCo2eq: 'kgCO2eq', kgSbeq: 'kgSbeq' } as const
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
  provider: string | null
  instanceType: string | null
  location: string | null
  lifespan: number
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
    return { gwp: zero(UNIT.kgCo2eq), pe: zero(UNIT.mj), adp: zero(UNIT.kgSbeq) }
  return {
    gwp: sumImpact(footprints.map(footprint => footprint.gwp)),
    pe: sumImpact(footprints.map(footprint => footprint.pe)),
    adp: sumImpact(footprints.map(footprint => footprint.adp)),
  }
}

function unitOrFallback(symbol: string | null | undefined, fallback: string): string {
  return symbol && KNOWN_UNITS.has(symbol) ? symbol : fallback
}

function footprintAmount(impact: FootprintImpact | null | undefined, fallback: string): Amount {
  if (!impact) return { value: 0, unit: fallback }
  return { value: totalValue(impact), unit: unitOrFallback(impact.unit, fallback) }
}

export function metricMean(metric: EcologitsMetric): number {
  return (metric.value.min + metric.value.max) / 2
}

const HOURS_PER_DAY = 24
const HOURS_PER_YEAR = 365 * HOURS_PER_DAY
const HOURS_PER_MONTH = Math.trunc(HOURS_PER_YEAR / 12)

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

function serviceImpact(assetName: string, impact: RawServiceImpact): ServiceImpact {
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
    if (!assetNames.has(asset.id)) assetNames.set(asset.id, asset.name)

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
      panels.push(kubernetesPanel(name, assetFootprint, kubernetesImpacts))
    }

    const llmImpacts = includedImpacts.filter(isLLMImpact)
    if (llmImpacts.length > 0) {
      llmFootprints.push(assetFootprint)
      panels.push(llmPanel(name, assetFootprint, llmImpacts))
    }
  }

  services.sort((a, b) => totalValue(b.footprint.gwp) - totalValue(a.footprint.gwp))

  const totalGwp = services.reduce((sum, service) => sum + totalValue(service.footprint.gwp), 0)
  const shares = new Map<string, number>()
  services.forEach(service => shares.set(service.label, totalGwp === 0 ? 0 : totalValue(service.footprint.gwp) / totalGwp))

  const kubernetesPanels = panels.filter((panel): panel is KubernetesPanel => panel.kind === 'kubernetes')
  const llmPanels = panels.filter((panel): panel is LLMPanel => panel.kind === 'llm')

  return {
    globalImpact: sumFootprints([...dockerFootprints, ...llmFootprints]),
    dockerImpact: sumFootprints(dockerFootprints),
    llmImpact: sumFootprints(llmFootprints),
    serviceImpacts: services,
    impactShares: shares,
    allServices,
    includedServices: [...included],
    assetPanels: panels,
    kubernetesPanels,
    llmPanels,
    serviceLines: services.map(serviceLine),
    assetCount: panels.length,
    serviceCount: services.length,
    dockerServiceCount: services.filter(service => service.assetType.family === 'KUBERNETES_CONTAINER').length,
    llmServiceCount: services.filter(service => service.assetType.family === 'LLM').length,
  }
}

function kubernetesPanel(name: string, footprint: Footprint, impacts: KubernetesImpact[]): KubernetesPanel {
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
    // Boavizta reports no energy metric for a server impact; only the LLM panel renders an energy total.
    energyTotal: { value: 0, unit: UNIT.kwh },
    peTotal: footprintAmount(footprint.pe, UNIT.mj),
    adpeTotal: footprintAmount(footprint.adp, UNIT.kgSbeq),
    provider: (profile.provider as string | undefined) ?? null,
    instanceType: (profile.instanceType ?? profile['instance-type'] ?? null) as string | null,
    location: (profile.location as string | undefined) ?? null,
    lifespan: lifetimeHours(profile.lifespan),
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

function llmPanel(name: string, footprint: Footprint, impacts: LLMImpact[]): LLMPanel {
  const first = impacts[0]
  const requestPerYear = impacts.reduce((sum, impact) => sum + Math.trunc(impact.requestCount), 0)
  const outputTokenCount = impacts.reduce((sum, impact) => sum + impact.outputTokenCount, 0)
  const requestCount = impacts.reduce((sum, impact) => sum + impact.requestCount, 0)

  let energy = 0
  let energyUnit: string = UNIT.kwh
  for (const impact of impacts) {
    const metric = impact.estimation?.impacts?.energy
    if (metric) {
      energy += metricMean(metric) * impact.requestCount
      energyUnit = unitOrFallback(metric.unit, UNIT.kwh)
    }
  }

  const providers = distinct(impacts.map(impact => impact.provider))
  return {
    kind: 'llm',
    name,
    type: first.assetType,
    regime: first.assetType.regime,
    serviceIds: impacts.map(impact => impact.serviceId),
    gwpTotal: footprintAmount(footprint.gwp, UNIT.kgCo2eq),
    energyTotal: { value: energy, unit: energyUnit },
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

function serviceLine(service: ServiceImpact): ServiceLine {
  return {
    service: service.service,
    regime: service.assetType.regime,
    family: service.assetType.family,
    gwp: footprintAmount(service.footprint.gwp, UNIT.kgCo2eq),
    energy: { value: 0, unit: UNIT.kwh },
    pe: footprintAmount(service.footprint.pe, UNIT.mj),
    adpe: footprintAmount(service.footprint.adp, UNIT.kgSbeq),
  }
}
