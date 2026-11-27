import type { ActivityRegime, ServiceFamily } from '@/domain/environment'
import type { KnownProfileOrigin, ProfileOrigin } from '@/domain/impact'

export interface FamilyDisplay {
  label: string
  /** The class of its `.asset-type-tag`. */
  css: string
  icon: string | null
}

/** How each family is shown, wherever assets are. */
export const FAMILIES: Record<ServiceFamily, FamilyDisplay> = {
  KUBERNETES_CONTAINER: { label: 'Kubernetes', css: 'kubernetes', icon: '/icons/docker.svg' },
  LLM: { label: 'LLM', css: 'llm', icon: '/icons/llm.svg' },
  UNKNOWN: { label: 'Other', css: 'measure', icon: null },
}

/** The order families are listed in. */
export const FAMILY_ORDER: readonly ServiceFamily[] = ['KUBERNETES_CONTAINER', 'LLM', 'UNKNOWN']

export const REGIME_LABELS: Record<ActivityRegime, string> = {
  MEASURED: 'Measured',
  MODELED: 'Modeled',
  UNKNOWN: 'Unknown',
}

/** What each family stands for, as its pill explains it on hover. */
export const FAMILY_HINTS: Record<ServiceFamily, string> = {
  KUBERNETES_CONTAINER: 'Workloads running as containers in a Kubernetes cluster: their activity is the CPU and memory they use, and their impacts are those of the servers they run on.',
  LLM: 'Inferences of large language models, hosted by a provider or self-hosted: their activity is the requests and tokens of each model.',
  UNKNOWN: 'Assets of no known family.',
}

/** How the activity of an asset is known, as its pill explains it on hover. */
export const REGIME_HINTS: Record<ActivityRegime, string> = {
  MEASURED: 'Its activity is measured on a running system: sampled from its metrics, or read from a provider\'s usage reports.',
  MODELED: 'Its activity is modeled from a declared profile, e.g. a yearly volume of requests shaped by temporal scenarios: for a system not running yet, or which cannot be observed.',
  UNKNOWN: 'How its activity is known is not told.',
}

/** Where the server profile an asset was evaluated with comes from. */
const PROFILE_ORIGIN_LABELS: Record<KnownProfileOrigin, string> = {
  DECLARED: 'Declared',
  NODES: 'Inferred from nodes',
  RESOURCE_QUOTAS: 'Inferred from resource quotas',
  CONTAINER_REQUESTS: 'Inferred from container requests',
  CONTAINER_LIMITS: 'Inferred from container limits',
}

const PROFILE_ORIGIN_TITLES: Record<KnownProfileOrigin, string> = {
  DECLARED: 'The server profile is the one declared on the asset',
  NODES: 'The hardware is inferred from the capacity of the cluster\'s nodes',
  RESOURCE_QUOTAS: 'The hardware is inferred from the resource quotas of the namespaces: a slice of the cluster',
  CONTAINER_REQUESTS: 'The hardware is inferred from the resources the containers request: a slice of the cluster',
  CONTAINER_LIMITS: 'The hardware is inferred from the resource limits of the containers: a slice of the cluster',
}

function isKnownProfileOrigin(origin: ProfileOrigin): origin is KnownProfileOrigin {
  return Object.hasOwn(PROFILE_ORIGIN_LABELS, origin)
}

/** The label of a profile origin; an origin of a module this UI does not know is shown as sent. */
export function profileOriginLabel(origin: ProfileOrigin): string {
  return isKnownProfileOrigin(origin) ? PROFILE_ORIGIN_LABELS[origin] : origin
}

/** What a profile origin means; an origin of a module this UI does not know is only said to be inferred. */
export function profileOriginTitle(origin: ProfileOrigin): string {
  return isKnownProfileOrigin(origin) ? PROFILE_ORIGIN_TITLES[origin] : `The server profile is inferred (${origin})`
}
