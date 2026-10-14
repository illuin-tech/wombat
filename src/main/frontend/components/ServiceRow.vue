<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { defaults, detailConfig } from '../utils/chart-options.js';
import { useTheme } from '../useTheme.js';

const props = defineProps({
  service: { type: Object, required: true },
  summary: { type: Object, required: true },
  index: { type: String, required: true }
});
const expanded = ref(false);
const summaryRow = ref(null);
const canvases = [];
let charts = [];
let disposed = false;
function render() {
  defaults();
  charts.forEach(chart => chart.destroy());
  charts = ['gwp', 'pe', 'adp'].map((metric, index) => new Chart(canvases[index], detailConfig(props.service, metric)));
}
async function toggle() {
  expanded.value = !expanded.value;
  summaryRow.value.setAttribute('aria-expanded', String(expanded.value));
  summaryRow.value.querySelector('.row-chevron').classList.toggle('open', expanded.value);
  if (!expanded.value) return;
  await nextTick();
  if (disposed) return;
  if (!charts.length) render();
  else charts.forEach(chart => { chart.resize(); chart.reset(); chart.update(); });
}
function keydown(event) {
  if (event.target === summaryRow.value && ['Enter', ' '].includes(event.key)) { event.preventDefault(); toggle(); }
}
onMounted(() => {
  // Keep the server's locale-aware metric cells; Vue owns expansion and chart lifecycle.
  summaryRow.value.append(...props.summary.childNodes);
});
useTheme(() => { if (charts.length) render(); });
onBeforeUnmount(() => { disposed = true; charts.forEach(chart => chart.destroy()); });
</script>

<template>
  <tr ref="summaryRow" class="service-summary" role="button" tabindex="0" aria-expanded="false"
      :data-idx="index" @click="toggle" @keydown="keydown"></tr>
  <tr :id="`detail-${index}`" v-show="expanded" class="service-detail">
    <td colspan="12">
      <div class="detail-charts">
        <div v-for="(metric, position) in ['GWP (kgCO2eq)', 'Primary Energy (MJ)', 'Abiotic Resources (kgSbeq)']"
             :key="metric" class="detail-chart-card">
          <div class="chart-title">{{ metric }}</div>
          <div class="detail-chart-wrap">
            <canvas :id="`detail${['Gwp', 'Pe', 'Adp'][position]}-${index}`" :ref="element => canvases[position] = element"></canvas>
          </div>
        </div>
      </div>
    </td>
  </tr>
</template>
