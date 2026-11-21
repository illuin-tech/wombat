import { assetTypeName, displayName, type AssetInfo, type AssetType } from '@/domain/environment'
import { PERIOD_LABELS, type EvaluationStatus, type Money, type SimulationEvaluation, type SimulationPeriod } from '@/domain/simulation'
import { serviceImpact, serviceModel, totalValue, type DashboardModel } from '@/pages/impact/model/aggregate'
import { asDecimal, asUserDate, formatUtcDayRange } from '@/services/format/formatters'

export const STATUS_LABELS: Record<EvaluationStatus, string> = {
  ok: 'Evaluated',
  'no-data': 'No data',
  'module-disabled': 'Module disabled',
  error: 'Error',
}

/** The tone of each status's chip. */
export const STATUS_TONES: Record<EvaluationStatus, string> = { ok: 'good', 'no-data': '', 'module-disabled': '', error: 'bad' }

/** One asset's line of the results, for the period shown. A value is null when it was not evaluated. */
export interface ResultRow {
  id: string
  name: string
  status: EvaluationStatus
  message: string | null
  gwp: number | null
  pe: number | null
  adp: number | null
  /** One amount per currency; empty when nothing was priced. */
  cost: Money[] | null
  /** Set for a measured asset whose activity misses part of the window. */
  coverageNote: string | null
}

/**
 * The dashboard's model of the period, from each service's footprint as the server scaled it. It has no asset panels:
 * those read activity, which is not scaled.
 */
export function dashboardModel(evaluation: SimulationEvaluation, period: SimulationPeriod, assets: readonly AssetInfo[]): DashboardModel {
  const names = new Map(assets.map(asset => [asset.id, displayName(asset)]))
  const evaluated = evaluation.assets.filter(result => result.status === 'ok' && (result.scaled[period]?.services?.length ?? 0) > 0)
  const services = evaluated.flatMap(result => (result.scaled[period]?.services ?? [])
    .flatMap(({ serviceId, assetType, impact }) => impact ? [serviceImpact(names.get(result.id) ?? result.id, { serviceId, assetType, footprint: impact })] : []))
  return serviceModel(services, evaluated.length)
}

/** A cost as shown: each currency's amount, e.g. "12.5 EUR + 3 USD". */
export function costText(cost: readonly Money[]): string {
  return cost.map(amount => `${asDecimal(amount.value)} ${amount.currency}`).join(' + ')
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

/** The period's totals over the assets evaluated `ok`, as the server summed them. */
export function totalRow(evaluation: SimulationEvaluation, period: SimulationPeriod): Pick<ResultRow, 'gwp' | 'pe' | 'adp' | 'cost'> {
  const { impact, cost } = evaluation.totals[period]
  return { gwp: impact && totalValue(impact.gwp), pe: impact && totalValue(impact.pe), adp: impact && totalValue(impact.adp), cost }
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
