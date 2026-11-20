import type { CoreDeploymentStatus, CoreInstance } from '@/domain/module'

export type ConvergenceState = 'waiting' | 'done' | 'failed' | 'timeout'

export interface Convergence {
  state: ConvergenceState
  /** How many live instances run the version, or a newer one. */
  converged: number
  total: number
  /** The instances on which the version failed to build. */
  failed: CoreInstance[]
}

/** How far the live instances are from running `version`. */
export function convergence(status: CoreDeploymentStatus, version: number): Convergence {
  const converged = status.instances.filter(instance => instance.deployedVersion >= version).length
  const failed = status.instances.filter(instance => instance.status === 'failed' && instance.deployedVersion < version)
  const state: ConvergenceState = failed.length > 0 ? 'failed' : converged === status.instances.length ? 'done' : 'waiting'
  return { state, converged, total: status.instances.length, failed }
}

/** Every instance polls once per interval: past two of them, and some slack, a lagging one is stuck. */
export function convergenceTimeoutMs(pollIntervalSeconds: number): number {
  return (2 * pollIntervalSeconds + 10) * 1000
}

export interface ConvergenceOptions {
  pollMs?: number
  sleep?: (ms: number) => Promise<void>
  now?: () => number
  onProgress?: (progress: Convergence) => void
  /** Stops polling: the last progress is returned as it is. */
  signal?: AbortSignal
}

/** Polls the deployment status until every live instance runs `version`, one failed to build it, or time runs out. */
export async function awaitConvergence(
  fetchStatus: () => Promise<CoreDeploymentStatus>,
  version: number,
  { pollMs = 2000, sleep = ms => new Promise(resolve => setTimeout(resolve, ms)), now = Date.now, onProgress, signal }: ConvergenceOptions = {},
): Promise<Convergence> {
  const start = now()
  for (;;) {
    const status = await fetchStatus()
    const progress = convergence(status, version)
    onProgress?.(progress)
    if (progress.state !== 'waiting' || signal?.aborted) return progress
    if (now() - start >= convergenceTimeoutMs(status.pollIntervalSeconds)) return { ...progress, state: 'timeout' }
    await sleep(pollMs)
  }
}

/** The instances which detect other modules than the one serving the page: their extension folders differ. */
export function divergentInstances(instances: readonly CoreInstance[]): CoreInstance[] {
  const reference = instances.find(instance => instance.current)
  if (!reference) return []
  const expected = [...reference.detectedModules].sort().join(',')
  return instances.filter(instance => [...instance.detectedModules].sort().join(',') !== expected)
}
