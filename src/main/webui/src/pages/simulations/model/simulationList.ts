import type { SimulationSummary } from '@/domain/simulation'

/** The simulations whose id, name, description or source environment contains the query. */
export function filterSimulations(simulations: readonly SimulationSummary[], query: string): SimulationSummary[] {
  const needle = query.trim().toLowerCase()
  if (!needle)
    return [...simulations]
  return simulations.filter(simulation => [simulation.id, simulation.name, simulation.description, simulation.parentEnvironmentName]
    .some(text => text?.toLowerCase().includes(needle)))
}

/** The name a duplicate is offered, e.g. `What-if (copy)`. */
export function duplicateName(name: string): string {
  return `${name} (copy)`
}
