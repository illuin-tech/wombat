<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { defaults, METRICS, timelineConfig } from '../utils/chart-options.js';
import { readPreference, writePreference } from '../utils/storage.js';
import { useTheme } from '../useTheme.js';

const props = defineProps({
  timeline: { type: Object, required: true },
  title: { type: String, required: true },
  note: { type: String, required: true },
  labels: { type: Array, required: true }
});
const metric = ref(props.timeline.series[0]?.key || 'gwp');
const mode = ref(readPreference('wombat.timeline.mode') === 'bar' ? 'bar' : 'line');
const accent = computed(() => METRICS[metric.value].accent);
const canvas = ref(null);
let chart;
function render() {
  defaults();
  chart?.destroy();
  chart = new Chart(canvas.value, timelineConfig(props.timeline,
    props.timeline.series.find(series => series.key === metric.value), mode.value));
}
watch([metric, mode], render);
function shape(value) { mode.value = value; writePreference('wombat.timeline.mode', value); }
useTheme(render);
onBeforeUnmount(() => chart?.destroy());
</script>

<template>
  <div class="chart-card" :class="accent">
    <div class="chart-head">
      <div class="chart-title">{{ title }}</div>
      <div class="chart-controls">
        <div id="timelinePills" class="chart-pills" role="group" aria-label="Metric shown">
          <button v-for="series in labels" :key="series.key" type="button" class="chart-pill"
                  :data-series="series.key" :class="[METRICS[series.key].accent, { active: metric === series.key }]"
                  :aria-pressed="metric === series.key" @click="metric = series.key">{{ series.label }}</button>
        </div>
        <div id="timelineModes" class="chart-pills" role="group" aria-label="Chart shape">
          <button v-for="value in ['bar', 'line']" :key="value" type="button" class="chart-pill"
                  :data-mode="value" :class="[accent, { active: mode === value }]"
                  :aria-pressed="mode === value" @click="shape(value)">{{ value === 'bar' ? 'Bars' : 'Lines' }}</button>
        </div>
      </div>
    </div>
    <div class="chart-wrap timeline"><canvas id="chartImpactTimeline" ref="canvas"></canvas></div>
    <div class="chart-note">{{ note }}</div>
  </div>
</template>
