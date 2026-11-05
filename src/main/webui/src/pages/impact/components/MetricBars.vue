<script setup lang="ts">
import {computed} from 'vue'
import type {DashboardModel} from '@/pages/impact/model/aggregate'
import {buildServiceMetricData} from '@/pages/impact/model/dashboard'
import {useTheme} from '@/layout/theme/useTheme'
import ChartCanvas from '@/components/ChartCanvas.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'

const props = defineProps<{model?: DashboardModel | null; loading?: boolean; refreshing?: boolean}>()
const {isDark} = useTheme()
const LABEL_WRAP = 18
const metrics = [
  {key: 'gwp' as const, label: 'GWP — Global Warming Potential (kgCO2eq)', color: '--accent-green', fallback: '#16a34a', unit: 'kgCO2eq', className: 'green'},
  {key: 'pe' as const, label: 'Primary Energy (MJ)', color: '--accent-blue', fallback: '#2563eb', unit: 'MJ', className: 'blue'},
  {key: 'adp' as const, label: 'Abiotic Resource Depletion (kgSbeq)', color: '--accent-yellow', fallback: '#ca8a04', unit: 'kgSbeq', className: 'yellow'},
]
const maxLabelLines = computed(() => Math.max(1, ...(props.model?.serviceImpacts ?? []).map(service => wrapLabel(service.service).length)))
const fullHeight = computed(() => Math.max(130, (props.model?.serviceImpacts.length ?? 0) * (6 + maxLabelLines.value * 12) + 48))
const height = computed(() => Math.min(fullHeight.value, 320))

function wrapLabel(label: string): string[] {
  const lines: string[] = []
  let current = ''
  for (const word of String(label).split(' ')) {
    if (current && `${current} ${word}`.length > LABEL_WRAP) {
      lines.push(current)
      current = word
    } else {
      current = current ? `${current} ${word}` : word
    }
  }
  if (current) lines.push(current)
  return lines
}

function colorOf(variable: string, fallback: string): string {
  isDark.value
  return getComputedStyle(document.documentElement).getPropertyValue(variable).trim() || fallback
}

const createData = (key: 'gwp' | 'pe' | 'adp', variable: string, fallback: string) => {
  const color = colorOf(variable, fallback)
  if (!props.model) return {labels: [], datasets: []}
  const data = buildServiceMetricData(props.model, key)
  return {
    ...data,
    datasets: data.datasets.map((dataset, index) => ({...dataset, backgroundColor: `${color}${index === 0 ? 'cc' : '33'}`, borderColor: color, borderWidth: 1})),
  }
}

const options = (unit: string, variable: string, fallback: string) => {
  const color = colorOf(variable, fallback)
  return {
  responsive: true,
  maintainAspectRatio: false,
  indexAxis: 'y' as const,
  datasets: {bar: {categoryPercentage: 0.5, barPercentage: 0.9, maxBarThickness: 12}},
  plugins: {
    legend: {position: 'top' as const, labels: {boxWidth: 10, padding: 14}},
    tooltip: {mode: 'index' as const, callbacks: {title: (items: {dataIndex: number}[]) => {
      const service = props.model?.serviceImpacts[items[0]?.dataIndex]
      return service ? service.label : ''
    }}},
  },
  scales: {
    x: {stacked: true, grid: {color: getComputedStyle(document.documentElement).getPropertyValue('--border').trim()}, title: {display: true, text: unit, color}},
    y: {stacked: true, grid: {color: getComputedStyle(document.documentElement).getPropertyValue('--border').trim()}, ticks: {
      font: {size: 11, lineHeight: 1.05},
      callback: (value: string | number) => wrapLabel(props.model?.serviceImpacts[Number(value)]?.service ?? String(value)),
    }},
  },
  }
}
</script>

<template>
  <div class="metrics-row" :class="{'is-refreshing': refreshing}">
    <div v-for="metric in metrics" :key="metric.key" class="chart-card" :class="metric.className">
      <div class="chart-title">{{ metric.label }}</div>
        <div class="chart-wrap hbar" :style="{height: `${height}px`}">
          <div class="hbar-inner" :style="{height: `${fullHeight}px`}">
            <SkeletonBlock v-if="loading" height="100%"/>
            <ChartCanvas v-else-if="model" type="bar" :data="createData(metric.key, metric.color, metric.fallback)" :options="options(metric.unit, metric.color, metric.fallback)"/>
        </div>
      </div>
    </div>
  </div>
</template>