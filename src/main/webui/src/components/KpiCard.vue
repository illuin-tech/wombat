<script setup lang="ts">
defineProps<{
  label: string
  value: string
  unit: string
  tone?: string
  sideValues: {label: string; value: string; icon: string; alt: string}[]
  splitValues: {label: string; value: string}[]
}>()
</script>

<template>
  <div class="kpi-card" :class="tone">
    <div class="kpi-label">{{ label }}</div>
    <div class="kpi-value-row">
      <div class="kpi-main"><div class="kpi-value">{{ value }}</div><div class="kpi-unit">{{ unit }}</div></div>
      <div v-for="item in sideValues" :key="item.label" class="kpi-split-item">
        <span class="kpi-side-val">{{ item.value }}</span>
        <span class="kpi-split-key"><img class="svc-icon" :src="item.icon" :alt="item.alt" />{{ item.label }}</span>
      </div>
    </div>
    <div class="kpi-split">
      <div v-for="item in splitValues" :key="item.label" class="kpi-split-item">
        <span class="kpi-split-key">{{ item.label }}</span><span class="kpi-split-val">{{ item.value }}</span>
      </div>
    </div>
  </div>
</template>