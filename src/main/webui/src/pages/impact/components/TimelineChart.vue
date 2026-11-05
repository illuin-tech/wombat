<script setup lang="ts">
import {computed, ref} from 'vue'
import type {TimelineResponse} from '@/domain/impact'
import {buildTimelineDatasets, buildTimelineLabels} from '@/pages/impact/model/dashboard'
import {useTheme} from '@/layout/theme/useTheme'
import ChartCanvas from '@/components/ChartCanvas.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'

const EMPTY_TIMELINE: TimelineResponse = {stepMs: 0, stepLabel: '', stepPlural: '', bucketStarts: [], series: []}
const props = withDefaults(defineProps<{timeline?: TimelineResponse | null; loading?: boolean; refreshing?: boolean}>(), {timeline: null, loading: false, refreshing: false})
const timeline = computed(() => props.timeline ?? EMPTY_TIMELINE)
const {isDark} = useTheme()
const metric = ref<TimelineResponse['series'][number]['key']>(timeline.value.series[0]?.key ?? 'gwp')
const mode = ref<'bar' | 'line'>(initialMode())
const metricStyles = {
  gwp: {variable: '--accent-green', fallback: '#16a34a', className: 'green'},
  pe: {variable: '--accent-blue', fallback: '#2563eb', className: 'blue'},
  adp: {variable: '--accent-yellow', fallback: '#ca8a04', className: 'yellow'},
}
const currentSeries = computed(() => timeline.value.series.find(series => series.key === metric.value))
const currentStyle = computed(() => metricStyles[metric.value] ?? metricStyles.gwp)
const cardClass = computed(() => currentStyle.value.className)

function initialMode(): 'bar' | 'line' {
  try {
    const stored = localStorage.getItem('wombat.timeline.mode')
    if (stored === 'bar' || stored === 'line') return stored
  } catch {
    // Keep the default when storage is unavailable.
  }
  return 'line'
}

function cssValue(name: string, fallback: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
}

function accent(style: typeof metricStyles[keyof typeof metricStyles]): string {
  return cssValue(style.variable, style.fallback)
}

function amount(value: number): string {
  if (!value) return '0'
  return Math.abs(value) < 0.001 ? value.toExponential(2) : String(Number(value.toPrecision(3)))
}

function tick(value: number): string {
  if (!value) return '0'
  return Math.abs(value) < 0.001 ? value.toExponential(1) : String(Number(value.toPrecision(3)))
}

const data = computed(() => {
  const color = accent(currentStyle.value)
  const stroke = cssValue('--text-dim', isDark.value ? '#6b8478' : '#9ab4a6')
  const useAlpha = isDark.value ? '80' : '33'
  const dense = timeline.value.bucketStarts.length > 120
  return {
    labels: buildTimelineLabels(timeline.value),
    datasets: buildTimelineDatasets(timeline.value, metric.value).map((dataset, index) => ({
      ...dataset,
      backgroundColor: `${color}${index === 0 ? 'cc' : useAlpha}`,
      borderColor: mode.value === 'line' ? stroke : color,
      borderWidth: mode.value === 'line' ? 1.25 : dense ? 0 : 1,
      fill: mode.value === 'line' ? (index === 0 ? 'origin' as const : '-1' as const) : false,
      pointRadius: 0,
      pointHoverRadius: mode.value === 'line' ? 3 : 0,
      ...(mode.value === 'line' ? {cubicInterpolationMode: 'monotone' as const} : {}),
    })),
  }
})

const options = computed(() => {
  const series = currentSeries.value
  const color = accent(currentStyle.value)
  const border = cssValue('--border', isDark.value ? '#2a3a34' : '#e3ede8')
  const text = cssValue('--text-muted', isDark.value ? '#a3bbb0' : '#4d7060')
  const hourly = timeline.value.stepMs < 86_400_000
  const tickFormat = new Intl.DateTimeFormat('en-US', hourly
    ? {timeZone: 'UTC', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23'}
    : {timeZone: 'UTC', month: 'short', day: 'numeric'})
  const titleFormat = new Intl.DateTimeFormat('en-US', hourly
    ? {timeZone: 'UTC', year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23'}
    : {timeZone: 'UTC', year: 'numeric', month: 'short', day: 'numeric'})
  return {
    responsive: true,
    maintainAspectRatio: false,
    datasets: {bar: {categoryPercentage: 0.9, barPercentage: 0.95}},
    interaction: {mode: 'index' as const, intersect: false},
    plugins: {
      legend: {position: 'top' as const, labels: {boxWidth: 10, padding: 14}},
      tooltip: {callbacks: {
        title: (items: {dataIndex: number}[]) => {
          const value = timeline.value.bucketStarts[items[0]?.dataIndex]
          return value === undefined ? '' : `${titleFormat.format(new Date(value))} UTC`
        },
        label: (context: {dataset: {label?: string}; parsed: {y: number | null}}) => ` ${context.dataset.label}: ${amount(context.parsed.y ?? 0)} ${series?.unit ?? ''}`,
        footer: (items: {parsed: {y: number | null}}[]) => `Total: ${amount(items.reduce((sum, item) => sum + (item.parsed.y ?? 0), 0))} ${series?.unit ?? ''}`,
      }},
    },
    scales: {
      x: {stacked: true, grid: {display: false}, ticks: {autoSkip: true, maxTicksLimit: 12, maxRotation: 0, font: {size: 11}}},
      y: {
        stacked: true,
        beginAtZero: true,
        grid: {color: border},
        title: {display: true, text: series?.unit ?? '', color},
        ticks: {font: {size: 11}, color: text, callback: (value: string | number) => tick(Number(value))},
      },
    },
  }
})

function setMode(next: 'bar' | 'line'): void {
  mode.value = next
  try {
    localStorage.setItem('wombat.timeline.mode', next)
  } catch {
    // Keep the selected mode for this page when storage is unavailable.
  }
}
</script>

<template>
  <div class="chart-card" :class="[cardClass, {'is-refreshing': refreshing}]">
    <div class="chart-head">
      <div class="chart-title">Impact over time<span v-if="!loading"> — {{ timeline.stepLabel }}</span></div>
      <div v-if="loading" class="chart-controls">
        <div class="chart-pills"><SkeletonBlock width="46px" height="24px"/><SkeletonBlock width="46px" height="24px"/></div>
        <div class="chart-pills"><SkeletonBlock width="42px" height="24px"/><SkeletonBlock width="42px" height="24px"/></div>
      </div>
      <div v-else class="chart-controls">
        <div class="chart-pills" id="timelinePills" role="group" aria-label="Metric shown">
          <button v-for="series in timeline.series" :key="series.key" type="button" class="chart-pill" :class="[{active: metric === series.key}, metricStyles[series.key]?.className]" :aria-pressed="metric === series.key" @click="metric = series.key">{{ series.label }}</button>
        </div>
        <div class="chart-pills" id="timelineModes" role="group" aria-label="Chart shape">
          <button type="button" class="chart-pill" :class="[{active: mode === 'bar'}, cardClass]" :aria-pressed="mode === 'bar'" @click="setMode('bar')">Bars</button>
          <button type="button" class="chart-pill" :class="[{active: mode === 'line'}, cardClass]" :aria-pressed="mode === 'line'" @click="setMode('line')">Lines</button>
        </div>
      </div>
    </div>
    <div v-if="loading" class="chart-wrap timeline"><SkeletonBlock height="260px"/></div>
    <div v-if="loading" class="chart-note"><SkeletonBlock width="350px" height="12px"/></div>
    <template v-else>
      <div class="chart-wrap timeline"><ChartCanvas :type="mode" :data="data" :options="options"/></div>
      <div class="chart-note">The footprint evaluated over each of the {{ timeline.bucketStarts.length }} {{ timeline.stepPlural }} of the range.</div>
    </template>
  </div>
</template>
