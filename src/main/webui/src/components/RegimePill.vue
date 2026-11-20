<script setup lang="ts">
import { REGIME_LABELS } from '@/domain/assetTypeDisplay'
import type { ActivityRegime } from '@/domain/environment'

defineProps<{ regime: ActivityRegime }>()

const TITLES: Record<ActivityRegime, string> = {
  MEASURED: 'Its activity is measured on the running system',
  MODELED: 'Its activity is modeled from a declared profile',
  UNKNOWN: 'How its activity is known is not told',
}
</script>

<template>
  <span class="icon-pill" :class="regime.toLowerCase()" :title="TITLES[regime]">
    <svg v-if="regime === 'MEASURED'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M21.3 15.3a2.4 2.4 0 0 1 0 3.4l-2.6 2.6a2.4 2.4 0 0 1-3.4 0L2.7 8.7a2.4 2.4 0 0 1 0-3.4l2.6-2.6a2.4 2.4 0 0 1 3.4 0Z" />
      <path d="m14.5 12.5 2-2" />
      <path d="m11.5 9.5 2-2" />
      <path d="m8.5 6.5 2-2" />
      <path d="m17.5 15.5 2-2" />
    </svg>
    <svg v-else-if="regime === 'MODELED'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M8.3 10a.7.7 0 0 1-.6-1.1L11.4 3a.7.7 0 0 1 1.2 0l3.7 5.9a.7.7 0 0 1-.6 1.1Z" />
      <rect x="3" y="14" width="7" height="7" rx="1" />
      <circle cx="17.5" cy="17.5" r="3.5" />
    </svg>
    {{ REGIME_LABELS[regime] }}
  </span>
</template>
