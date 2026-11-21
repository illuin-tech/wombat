import { assetTypeName, displayName, type AssetInfo, type AssetType } from '@/domain/environment'
import type { AssetImpact, Footprint, FootprintImpact } from '@/domain/impact'
import { PERIOD_LABELS, type EvaluationStatus, type SimulationEvaluation, type SimulationPeriod } from '@/domain/simulation'
import { aggregate, totalValue, type DashboardModel } from '@/pages/impact/model/aggregate'
import { asUserDate, formatUtcDayRange } from '@/services/format/formatters'

export const STATUS_LABELS: Record<EvaluationStatus, string> = {
  ok: 'Evaluated',
  'no-data': 'No data',
  'module-disabled': 'Module disabled',
  error: 'Error',
}

/** One asset's line of the results, for the period shown. A value is null when it was not evaluated. */
export interface ResultRow {
  id: string
  name: string
  status: EvaluationStatus
  message: string | null
  gwp: number | null
  pe: number | null
  adp: number | null
  cost: { value: number; unit: string } | null
  /** Set for a measured asset whose activity misses part of the window. */
  coverageNote: string | null
}

/**
 * The dashboard's model of the period: each service's footprint over the window, times the period's factor. Only the
 * footprints are scaled, so the parts of the model that read activity (the asset panels) are not to be shown.
 */
export function dashboardModel(evaluation: SimulationEvaluation, period: SimulationPeriod, assets: readonly AssetInfo[]): DashboardModel {
  const factor = evaluation.factors[period]
  const impacts = evaluation.assets
    .filter(result => result.status === 'ok')
    .flatMap(result => result.result)
    .filter((evaluated): evaluated is AssetImpact & { type: 'IMPACT' } => evaluated.type === 'IMPACT')
    .map(impact => ({
      ...impact,
      footprint: scaleFootprint(impact.footprint, factor),
      serviceImpacts: impact.serviceImpacts.map(service => ({ ...service, footprint: scaleFootprint(service.footprint, factor) })),
    }))
  return aggregate(impacts, [...assets])
}

function scaleFootprint(footprint: Footprint, factor: number): Footprint {
  const scale = (impact: FootprintImpact | null): FootprintImpact | null => impact && {
    ...impact,
    embedded: { ...impact.embedded, value: impact.embedded.value * factor },
    use: { ...impact.use, value: impact.use.value * factor },
  }
  return { gwp: scale(footprint.gwp), pe: scale(footprint.pe), adp: scale(footprint.adp) }
}

/** Every evaluated asset, by name. */
export function resultRows(
  evaluation: SimulationEvaluation,
  period: SimulationPeriod,
  assets: readonly AssetInfo[],
  typesByName: ReadonlyMap<string, AssetType>,
): ResultRow[] {
  const byId = new Map(assets.map(asset => [asset.id, asset]))
  return evaluation.assets
    .map(result => {
      const asset = byId.get(result.id)
      const impact = result.scaled[period]?.impact ?? null
      const measured = !!asset && typesByName.get(assetTypeName(asset) ?? '')?.regime === 'MEASURED'
      return {
        id: result.id,
        name: asset ? displayName(asset) : result.id,
        status: result.status,
        message: result.message ?? null,
        gwp: impact && totalValue(impact.gwp),
        pe: impact && totalValue(impact.pe),
        adp: impact && totalValue(impact.adp),
        cost: result.scaled[period]?.cost ?? null,
        coverageNote: measured ? coverageNote(result.coverage) : null,
      }
    })
    .sort((a, b) => a.name.localeCompare(b.name))
}

/* A gap in measured activity counts as none, so the projection is too low by that much */
function coverageNote(coverage: number | null | undefined): string | null {
  if (coverage === null || coverage === undefined || coverage >= 1) return null
  const percent = Math.min(99, Math.max(1, Math.round(coverage * 100)))
  return `Activity found for ${percent}% of the window only: the rest counts as none.`
}

/** What the results were evaluated on, and what they are scaled to. */
export function windowText(evaluation: SimulationEvaluation, period: SimulationPeriod): string {
  const { start, end, period: window } = evaluation.window
  const range = window === 'DAY' ? `${asUserDate(start)} → ${asUserDate(end)}` : formatUtcDayRange(start, end)
  const scaling = window === period ? '' : `, scaled to a ${PERIOD_LABELS[period].toLowerCase()}`
  return `Evaluated over ${range}${scaling}.`
}
