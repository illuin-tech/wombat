<script setup lang="ts">
import {Chart, registerables, type ChartData, type ChartOptions, type ChartType} from 'chart.js'
import {onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useTheme} from '@/layout/theme/useTheme'

Chart.register(...registerables)
const props = defineProps<{ type: ChartType; data: ChartData; options?: ChartOptions }>()
const canvas = ref<HTMLCanvasElement | null>(null)
const {isDark} = useTheme()
let chart: Chart | null = null

function render(): void {
  if (!canvas.value) return
  const styles = getComputedStyle(document.documentElement)
  Chart.defaults.color = styles.getPropertyValue('--text-muted').trim() || (isDark.value ? '#94a3b8' : '#4d7060')
  Chart.defaults.borderColor = styles.getPropertyValue('--border').trim() || (isDark.value ? '#334155' : '#e3ede8')
  Chart.defaults.font.family = "'DM Sans', sans-serif"
  Chart.defaults.font.size = 12
  Chart.defaults.font.weight = 500
  chart?.destroy()
  chart = new Chart(canvas.value, {type: props.type, data: props.data, options: props.options})
}

onMounted(render)
watch(() => [props.type, props.data, props.options, isDark.value], render, {deep: true})
onBeforeUnmount(() => chart?.destroy())
</script>

<template>
  <canvas ref="canvas"/>
</template>