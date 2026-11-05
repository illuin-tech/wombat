<script setup lang="ts">
import {ref} from 'vue'
import type {DashboardModel, ServiceImpact} from '@/pages/impact/model/aggregate'
import {totalValue} from '@/pages/impact/model/aggregate'
import {useTheme} from '@/layout/theme/useTheme'
import {asDecimal, asPercent} from '@/services/format/formatters'
import ChartCanvas from '@/components/ChartCanvas.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'

const props = defineProps<{model?: DashboardModel | null; loading?: boolean; refreshing?: boolean}>()
const {isDark} = useTheme()
const open = ref(new Set<string>())
const skeletonRows = [0, 1, 2, 3, 4]
const metrics = [
  {key: 'gwp' as const, label: 'GWP', unit: 'kgCO2eq', color: '--accent-green', fallback: '#16a34a'},
  {key: 'pe' as const, label: 'Primary Energy', unit: 'MJ', color: '--accent-blue', fallback: '#2563eb'},
  {key: 'adp' as const, label: 'Abiotic Resources', unit: 'kgSbeq', color: '--accent-yellow', fallback: '#ca8a04'},
]

function value(service: ServiceImpact, key: 'gwp' | 'pe' | 'adp'): number {
  return totalValue(service.footprint[key])
}

function toggle(label: string): void {
  const next = new Set(open.value)
  next.has(label) ? next.delete(label) : next.add(label)
  open.value = next
}

function share(label: string): string {
  const value = props.model?.impactShares.get(label)
  return value === undefined ? '—' : asPercent(value)
}

function colorOf(metric: typeof metrics[number]): string {
  isDark.value
  return getComputedStyle(document.documentElement).getPropertyValue(metric.color).trim() || metric.fallback
}

function detailData(service: ServiceImpact, metric: typeof metrics[number]) {
  const footprint = service.footprint[metric.key]
  const color = colorOf(metric)
  return {
    labels: ['Embedded', 'Use'],
    datasets: [{data: [footprint?.embedded.value ?? 0, footprint?.use.value ?? 0], backgroundColor: [`${color}cc`, `${color}55`], borderColor: [color, color], borderWidth: 1}],
  }
}

function detailOptions(metric: typeof metrics[number]) {
  isDark.value
  const border = getComputedStyle(document.documentElement).getPropertyValue('--border').trim()
  const text = getComputedStyle(document.documentElement).getPropertyValue('--text-muted').trim()
  return {
    indexAxis: 'y' as const,
    responsive: true,
    maintainAspectRatio: false,
    plugins: {legend: {display: false}, tooltip: {mode: 'index' as const}},
    scales: {
      x: {grid: {color: border}, title: {display: true, text: metric.unit, color: colorOf(metric)}},
      y: {grid: {display: false}, ticks: {color: text, font: {size: 12, weight: 600}}},
    },
  }
}
</script>

<template>
  <div class="table-card" :class="{'is-refreshing': refreshing}">
    <div class="table-head">Per-service breakdown</div>
    <table>
      <thead>
        <tr>
          <th class="th-toggle"></th>
          <th>Service</th>
          <th>GWP total (kgCO2eq)</th>
          <th>GWP embedded</th>
          <th>GWP use</th>
          <th>PE total (MJ)</th>
          <th>PE embedded</th>
          <th>PE use</th>
          <th>ADP total (kgSbeq)</th>
          <th>ADP embedded</th>
          <th>ADP use</th>
          <th>GWP share</th>
        </tr>
      </thead>
      <tbody>
        <template v-if="loading">
          <tr v-for="row in skeletonRows" :key="row" class="service-summary">
            <td v-for="cell in 12" :key="cell"><SkeletonBlock :width="cell === 2 ? '130px' : '56px'" height="12px"/></td>
          </tr>
        </template>
        <template v-else-if="model">
        <template v-for="(service, index) in model.serviceImpacts" :key="service.label">
          <tr class="service-summary" :data-idx="index + 1" @click="toggle(service.label)">
            <td class="td-toggle"><span class="row-chevron" :class="{open: open.has(service.label)}">▶</span></td>
            <td class="id" :title="service.label">
              <img v-if="service.assetType.family === 'KUBERNETES_CONTAINER'" class="svc-icon" src="/icons/docker.svg" alt="container"/>
              <img v-else-if="service.assetType.family === 'LLM'" class="svc-icon" src="/icons/llm.svg" alt="llm"/>
              {{ service.service }}
            </td>
            <td class="metric gwp bold">{{ asDecimal(value(service, 'gwp')) }}</td>
            <td class="metric gwp use">{{ asDecimal(service.footprint.gwp?.embedded.value) }}</td>
            <td class="metric gwp use">{{ asDecimal(service.footprint.gwp?.use.value) }}</td>
            <td class="metric pe bold">{{ asDecimal(value(service, 'pe')) }}</td>
            <td class="metric pe use">{{ asDecimal(service.footprint.pe?.embedded.value) }}</td>
            <td class="metric pe use">{{ asDecimal(service.footprint.pe?.use.value) }}</td>
            <td class="metric adp bold">{{ asDecimal(value(service, 'adp')) }}</td>
            <td class="metric adp use">{{ asDecimal(service.footprint.adp?.embedded.value) }}</td>
            <td class="metric adp use">{{ asDecimal(service.footprint.adp?.use.value) }}</td>
            <td><span class="badge">{{ share(service.label) }}</span></td>
          </tr>
          <tr :id="`detail-${index + 1}`" class="service-detail" :class="{collapsed: !open.has(service.label)}">
            <td colspan="12">
              <div v-if="open.has(service.label)" class="detail-charts">
                <div v-for="metric in metrics" :key="metric.key" class="detail-chart-card">
                  <div class="chart-title">{{ metric.label }} ({{ metric.unit }})</div>
                  <div class="detail-chart-wrap"><ChartCanvas type="bar" :data="detailData(service, metric)" :options="detailOptions(metric)"/></div>
                </div>
              </div>
            </td>
          </tr>
        </template>
        </template>
      </tbody>
    </table>
  </div>
</template>
