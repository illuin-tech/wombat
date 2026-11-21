import { describe, expect, it } from 'vitest'
import type { SimulationSummary } from '@/domain/simulation'
import { duplicateName, filterSimulations } from './simulationList'

const simulation = (id: string, name: string, extra: Partial<SimulationSummary> = {}): SimulationSummary =>
  ({ id, name, referenceWindow: 'MONTH', assetCount: 0, ...extra })

const simulations = [
  simulation('bigger-cluster', 'Bigger cluster', { parentEnvironmentId: 'prod', parentEnvironmentName: 'Production' }),
  simulation('chatbot', 'Chatbot launch', { description: 'Estimates the **assistant**' }),
]

describe('simulationList', () => {
  it('filters on the id, name, description and source environment', () => {
    expect(filterSimulations(simulations, '').map(found => found.id)).toEqual(['bigger-cluster', 'chatbot'])
    expect(filterSimulations(simulations, 'CLUSTER').map(found => found.id)).toEqual(['bigger-cluster'])
    expect(filterSimulations(simulations, 'assistant').map(found => found.id)).toEqual(['chatbot'])
    expect(filterSimulations(simulations, 'production').map(found => found.id)).toEqual(['bigger-cluster'])
    expect(filterSimulations(simulations, 'nothing')).toEqual([])
  })

  it('names a duplicate after its source', () => {
    expect(duplicateName('What-if')).toBe('What-if (copy)')
  })
})
