<script setup lang="ts">
import {computed} from 'vue'
import type {DashboardModel} from '@/pages/impact/model/aggregate'
import {useTheme} from '@/layout/theme/useTheme'
import ChartCanvas from '@/components/ChartCanvas.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'

const props = defineProps<{model?: DashboardModel | null; loading?: boolean; refreshing?: boolean}>()
const {isDark} = useTheme()
const palette = ['--accent-green', '--accent-blue', '--accent-yellow', '#0891b2', '#7c3aed', '--accent-orange', '#db2777', '#0284c7']
const colors = computed(() => palette.map((color) => {
  isDark.value
  if (!color.startsWith('--')) return color
  return getComputedStyle(document.documentElement).getPropertyValue(color).trim() || color
}))
const shares = computed(() => (props.model?.serviceImpacts ?? []).map((service, index) => ({
  label: service.service,
  fullLabel: service.label,
  share: props.model?.impactShares.get(service.label) ?? 0,
  color: colors.value[index % colors.value.length],
})))
const data = computed(() => ({
  labels: shares.value.map(service => service.label),
  datasets: [{
    data: shares.value.map(service => service.share),
    backgroundColor: shares.value.map(service => `${service.color}cc`),
    borderColor: shares.value.map(service => service.color),
    borderWidth: 1,
    hoverOffset: 8,
  }],
}))
const options = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  cutout: '62%',
  plugins: {
    legend: {display: false},
    tooltip: {callbacks: {
      title: (items: {dataIndex: number}[]) => shares.value[items[0]?.dataIndex]?.fullLabel ?? '',
      label: (context: {parsed: number}) => ` ${(context.parsed * 100).toFixed(1)}%`,
    }},
  },
}))
</script>

<template>
  <div class="chart-card" :class="{'is-refreshing': refreshing}">
    <div class="chart-title">GWP shares</div>
    <div v-if="loading" class="chart-wrap"><SkeletonBlock height="230px"/></div>
    <div v-else-if="model" class="chart-wrap"><ChartCanvas type="doughnut" :data="data" :options="options"/></div>
    <div id="sharesLegend" v-if="loading" class="shares-legend"><SkeletonBlock v-for="item in 4" :key="item" width="90%" height="14px"/></div>
    <div v-else-if="model" id="sharesLegend" class="shares-legend">
      <div v-for="service in shares" :key="service.fullLabel" class="shares-legend-item" :title="service.fullLabel">
        <span class="shares-legend-dot" :style="{background: service.color}" />
        <span class="shares-legend-label">{{ service.label }}</span>
        <span class="shares-legend-pct">{{ (service.share * 100).toFixed(1) }}%</span>
      </div>
    </div>
  </div>
</template>