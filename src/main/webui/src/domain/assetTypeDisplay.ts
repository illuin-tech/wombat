import type { ActivityRegime, ServiceFamily } from '@/domain/environment'

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
