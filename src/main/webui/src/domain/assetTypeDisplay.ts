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
