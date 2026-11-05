<script setup lang="ts">
import {computed} from 'vue'
import {totalValue, type DashboardModel} from '@/pages/impact/model/aggregate'
import {asDecimal} from '@/services/format/formatters'
import KpiCard from '@/components/KpiCard.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'

const props = defineProps<{model?: DashboardModel | null; loading?: boolean; refreshing?: boolean}>()
const skeletonCards = ['green', 'blue', 'yellow']
const cards = computed(() => props.model ? [
  {label: 'Global GWP', value: totalValue(props.model.globalImpact.gwp), unit: 'kgCO2eq', tone: 'green', metric: 'gwp' as const},
  {label: 'Primary Energy', value: totalValue(props.model.globalImpact.pe), unit: 'MJ', tone: 'blue', metric: 'pe' as const},
  {label: 'Abiotic Resources', value: totalValue(props.model.globalImpact.adp), unit: 'kgSbeq', tone: 'yellow', metric: 'adp' as const},
] : [])

function sideValues(metric: 'gwp' | 'pe' | 'adp') {
  if (!props.model) return []
  return [
    {label: 'Docker', value: asDecimal(totalValue(props.model.dockerImpact[metric])), icon: '/icons/docker.svg', alt: 'container'},
    {label: 'LLM', value: asDecimal(totalValue(props.model.llmImpact[metric])), icon: '/icons/llm.svg', alt: 'llm'},
  ]
}

function splitValues(metric: 'gwp' | 'pe' | 'adp') {
  const impact = props.model?.globalImpact[metric]
  return [
    {label: 'Embedded', value: asDecimal(impact?.embedded.value)},
    {label: 'Use', value: asDecimal(impact?.use.value)},
  ]
}
</script>

<template>
  <div class="kpi-row" :class="{'is-refreshing': refreshing}">
    <template v-if="loading">
      <div v-for="tone in skeletonCards" :key="tone" class="kpi-card" :class="tone">
        <div class="kpi-label"><SkeletonBlock width="100px"/></div>
        <div class="kpi-value-row">
          <div class="kpi-main"><SkeletonBlock width="92px" height="30px"/><SkeletonBlock width="48px" height="12px"/></div>
          <div v-for="side in 2" :key="side" class="kpi-split-item"><SkeletonBlock width="42px" height="14px"/><SkeletonBlock width="52px" height="12px"/></div>
        </div>
        <div class="kpi-split"><div v-for="split in 2" :key="split" class="kpi-split-item"><SkeletonBlock width="54px" height="12px"/><SkeletonBlock width="48px" height="12px"/></div></div>
      </div>
    </template>
    <KpiCard v-else v-for="card in cards" :key="card.metric" :label="card.label" :value="asDecimal(card.value)" :unit="card.unit" :tone="card.tone" :side-values="sideValues(card.metric)" :split-values="splitValues(card.metric)" />
  </div>
</template>