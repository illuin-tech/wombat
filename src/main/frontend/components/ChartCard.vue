<script setup>
import { computed, onBeforeUnmount, ref } from 'vue';
import { chartColors, defaults, horizontalConfig, sharesConfig, wrapLabel } from '../utils/chart-options.js';
import { useTheme } from '../useTheme.js';

const props = defineProps({
  metric: { type: String, required: true },
  title: { type: String, required: true },
  color: { type: String, default: '' },
  services: { type: Array, required: true }
});
const canvas = ref(null);
const colors = ref([]);
const height = computed(() => {
  const lines = props.services.reduce((maximum, service) => Math.max(maximum, wrapLabel(service.name).length), 1);
  return Math.max(130, props.services.length * (6 + lines * 12) + 48);
});
let chart;
useTheme(() => {
  if (!window.Chart) throw new Error('Chart.js failed to load');
  defaults();
  colors.value = chartColors();
  chart?.destroy();
  chart = new Chart(canvas.value, props.metric === 'shares'
    ? sharesConfig(props.services) : horizontalConfig(props.services, props.metric));
});
onBeforeUnmount(() => chart?.destroy());
</script>

<template>
  <div class="chart-card" :class="color">
    <div class="chart-title">{{ title }}</div>
    <template v-if="metric === 'shares'">
      <div class="chart-wrap"><canvas id="chartSharesDonut" ref="canvas"></canvas></div>
      <div id="sharesLegend" class="shares-legend">
        <div v-for="(service, index) in services" :key="index" class="shares-legend-item" :title="`${service.asset} / ${service.name}`">
          <span class="shares-legend-dot" :style="{ background: colors[index % colors.length] }"></span>
          <span class="shares-legend-label">{{ service.name }}</span>
          <span class="shares-legend-pct">{{ (service.share * 100).toFixed(1) }}%</span>
        </div>
      </div>
    </template>
    <div v-else class="chart-wrap hbar" :style="{ height: `${Math.min(height, 320)}px` }">
      <div class="hbar-inner" :style="{ height: `${height}px` }"><canvas ref="canvas"></canvas></div>
    </div>
  </div>
</template>
