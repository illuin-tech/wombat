import { describe, expect, it } from 'vitest'
import type { AssetInfo, AssetType } from '@/domain/environment'
import type { Footprint, KubernetesImpact, LLMImpact } from '@/domain/impact'
import { PERIODS, type AssetEvaluation, type Money, type SimulationEvaluation } from '@/domain/simulation'
import { totalValue } from '@/pages/impact/model/aggregate'
import { costText, dashboardModel, resultRows, totalRow, windowText } from './results'

const type = (localName: string, regime: AssetType['regime'], family: AssetType['family']): AssetType =>
  ({ namespace: 'n', localName, name: `n.${localName}`, regime, family })
const kubernetes = type('kubernetes', 'MEASURED', 'KUBERNETES_CONTAINER')
const llmStatic = type('llm-static', 'MODELED', 'LLM')
const typesByName = new Map([kubernetes, llmStatic].map(t => [t.name, t]))
const assets: AssetInfo[] = [
  { id: 'cluster', name: 'Cluster', type: kubernetes.name },
  { id: 'chatbot', name: 'Chatbot', type: llmStatic.name },
  { id: 'vertex', name: 'Vertex', type: 'n.llm-vertexai' },
]

function footprint(gwp: number): Footprint {
  const impact = (value: number, unit: string) => ({ unit, description: '', embedded: { value: value / 4 }, use: { value: value * 3 / 4 } })
  return { gwp: impact(gwp, 'kgCO2eq'), pe: impact(gwp * 10, 'MJ'), adp: impact(gwp / 10, 'kgSbeq'), energy: null }
}

const container = (serviceId: string, gwp: number): KubernetesImpact => ({
  serviceId, assetType: kubernetes, footprint: footprint(gwp), profile: null, share: 0.5, location: null, serverSpecification: null, loadPercent: 20, nodeCount: 1,
})
const model = (serviceId: string, gwp: number): LLMImpact => ({
  serviceId, assetType: llmStatic, footprint: footprint(gwp), profile: null, provider: 'mistral', model: serviceId, location: 'FR', outputTokenCount: 100, requestCount: 10,
})
/* Over the window, a month, as the evaluator returns it */
const impact = (services: (KubernetesImpact | LLMImpact)[]): AssetEvaluation => ({
  type: 'IMPACT',
  footprint: footprint(services.reduce((sum, service) => sum + totalValue(service.footprint.gwp), 0)),
  serviceImpacts: services,
  provider: services[0].assetType.family === 'LLM' ? 'ECOLOGITS' : 'BOAVIZTA',
  range: null,
  profileOrigin: 'DECLARED',
  warnings: [],
})
/* As the server scales them: each service's footprint, and the asset's cost in euros, per day, times the period's days */
const DAYS = { DAY: 1, WEEK: 7, MONTH: 30, YEAR: 365 }
const NOTHING: Money[] = []
const scaled = (services: [string, AssetType, number][], eurosPerDay?: number) => Object.fromEntries(PERIODS.map(period => [period, {
  impact: footprint(services.reduce((sum, [, , perDay]) => sum + perDay, 0) * DAYS[period]),
  cost: eurosPerDay === undefined ? NOTHING : [{ value: eurosPerDay * DAYS[period], currency: 'EUR' }],
  services: services.map(([serviceId, assetType, perDay]) => ({ serviceId, assetType, impact: footprint(perDay * DAYS[period]), cost: NOTHING })),
}])) as SimulationEvaluation['totals']

const evaluation: SimulationEvaluation = {
  window: { start: '2026-10-22T00:00:00Z', end: '2026-11-21T00:00:00Z', period: 'MONTH' },
  factors: { DAY: 1 / 30, WEEK: 7 / 30, MONTH: 1, YEAR: 365 / 30 },
  totals: Object.fromEntries(PERIODS.map(period => [period, { impact: footprint(DAYS[period]), cost: [{ value: 2 * DAYS[period], currency: 'EUR' }] }])) as SimulationEvaluation['totals'],
  assets: [
    {
      id: 'cluster', status: 'ok', coverage: 0.5,
      result: [impact([container('web', 9), container('worker', 3)])],
      scaled: scaled([['web', kubernetes, 0.3], ['worker', kubernetes, 0.1]]),
    },
    {
      id: 'chatbot', status: 'ok', coverage: 0.5,
      result: [impact([model('mistral-large', 18)]), { type: 'COST' }],
      scaled: scaled([['mistral-large', llmStatic, 0.6]], 2),
    },
    { id: 'vertex', status: 'module-disabled', message: 'No module runs n.llm-vertexai', result: [], scaled: {} },
  ],
}

describe('dashboardModel', () => {
  it('takes each service\'s footprint as the server scaled it to the period, and folds them as the dashboard does', () => {
    const day = dashboardModel(evaluation, 'DAY', assets)

    expect(totalValue(day.globalImpact.gwp)).toBeCloseTo(1)
    expect(totalValue(day.dockerImpact.gwp)).toBeCloseTo(0.4)
    expect(totalValue(day.llmImpact.pe)).toBeCloseTo(6)
    expect(day.serviceImpacts.map(service => [service.label, Number(totalValue(service.footprint.gwp).toFixed(3))])).toEqual([
      ['Chatbot / mistral-large', 0.6],
      ['Cluster / web', 0.3],
      ['Cluster / worker', 0.1],
    ])
    expect(day.impactShares.get('Cluster / web')).toBeCloseTo(0.3)
  })

  it('never reads the window\'s results, which it would have to scale', () => {
    const withoutResults = { ...evaluation, assets: evaluation.assets.map(result => ({ ...result, result: [] })) }

    expect(totalValue(dashboardModel(withoutResults, 'MONTH', assets).globalImpact.gwp)).toBeCloseTo(30)
    expect(dashboardModel(withoutResults, 'MONTH', assets).serviceCount).toBe(3)
  })
})

describe('resultRows', () => {
  it('lists every asset by name with the period\'s values, and notes the coverage of measured ones only', () => {
    const rows = resultRows(evaluation, 'YEAR', assets, typesByName)

    expect(rows.map(row => [row.name, row.status, row.gwp, row.cost])).toEqual([
      ['Chatbot', 'ok', 219, [{ value: 730, currency: 'EUR' }]],
      ['Cluster', 'ok', 146, []],
      ['Vertex', 'module-disabled', null, null],
    ])
    expect(rows[0].coverageNote).toBeNull()
    expect(rows[1].coverageNote).toBe('Activity found for 50% of the window only: the rest counts as none.')
    expect(rows[2].message).toBe('No module runs n.llm-vertexai')
  })

  it('names an asset deleted since by its id, and never rounds a partial coverage to 0% or 100%', () => {
    const rows = resultRows({ ...evaluation, assets: [{ ...evaluation.assets[0], coverage: 0.999 }, { ...evaluation.assets[0], id: 'gone', coverage: 0.001 }] }, 'DAY', assets, typesByName)

    expect(rows.map(row => [row.name, row.coverageNote])).toEqual([
      ['Cluster', 'Activity found for 99% of the window only: the rest counts as none.'],
      ['gone', null],
    ])
  })
})

describe('totalRow', () => {
  it('reads the period\'s totals as the server summed them', () => {
    expect(totalRow(evaluation, 'WEEK')).toEqual({ gwp: 7, pe: 70, adp: 0.7, cost: [{ value: 14, currency: 'EUR' }] })
  })
})

describe('costText', () => {
  it('shows each currency\'s amount, never converted', () => {
    expect(costText([{ value: 12.5, currency: 'EUR' }, { value: 3, currency: 'USD' }])).toBe('12.5 EUR + 3 USD')
  })
})

describe('windowText', () => {
  it('tells the window, and the period the results are scaled to', () => {
    expect(windowText(evaluation, 'MONTH')).toBe('Evaluated over Oct 22, 2026 → Nov 20, 2026 (UTC).')
    expect(windowText(evaluation, 'YEAR')).toBe('Evaluated over Oct 22, 2026 → Nov 20, 2026 (UTC), scaled to a year.')
    const day = { ...evaluation, window: { start: '2026-11-20T14:00:00Z', end: '2026-11-21T14:00:00Z', period: 'DAY' as const } }
    expect(windowText(day, 'WEEK')).toBe('Evaluated over Nov 20, 2026 14:00 UTC → Nov 21, 2026 14:00 UTC, scaled to a week.')
  })
})
