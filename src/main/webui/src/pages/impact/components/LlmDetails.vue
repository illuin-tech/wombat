<script setup lang="ts">
import type {LLMPanel} from '@/pages/impact/model/aggregate'
import {asDecimal} from '@/services/format/formatters'

defineProps<{panel: LLMPanel}>()
</script>

<template>
  <div class="card-section">
    <div class="card-section-label">Services / Models</div>
    <div class="service-tags">
      <span v-for="service in panel.serviceIds" :key="service" class="service-tag"><img class="svc-icon" src="/icons/llm.svg" alt="llm"/>{{ service }}</span>
    </div>
  </div>
  <div class="params-inline">
    <div v-if="panel.provider" class="param-item"><span class="param-key">Provider</span><span class="param-val">{{ panel.provider }}</span></div>
    <div v-if="panel.location" class="param-item"><span class="param-key">Location</span><span class="param-val">{{ panel.location }}</span></div>
    <div class="param-item"><span class="param-key">Output tokens total</span><span class="param-val">{{ asDecimal(panel.outputTokenCount) }}</span></div>
    <div v-if="panel.regime === 'MODELED' && panel.requestPerYear > 0" class="param-item"><span class="param-key">Estimated requests / year</span><span class="param-val">{{ asDecimal(panel.requestPerYear) }}</span></div>
  </div>
  <div class="card-section asset-load">
    <div class="card-section-label">Usage over period</div>
    <div class="asset-load-row">
      <template v-if="panel.regime === 'MODELED'">
        <div class="param-item"><span class="param-key">Requests</span><span class="param-val">{{ asDecimal(panel.requestCount) }}</span></div>
        <div class="param-item"><span class="param-key">GWP / request</span><span class="param-val">{{ asDecimal(panel.gwpPerRequest.value) }} {{ panel.gwpPerRequest.unit }}</span></div>
      </template>
      <div class="param-item"><span class="param-key">GWP total</span><span class="param-val">{{ asDecimal(panel.gwpTotal.value) }} {{ panel.gwpTotal.unit }}</span></div>
      <div class="param-item"><span class="param-key">Energy total</span><span class="param-val">{{ asDecimal(panel.energyTotal.value) }} {{ panel.energyTotal.unit }}</span></div>
      <div class="param-item"><span class="param-key">PE total</span><span class="param-val">{{ asDecimal(panel.peTotal.value) }} {{ panel.peTotal.unit }}</span></div>
      <div class="param-item"><span class="param-key">ADPe total</span><span class="param-val">{{ asDecimal(panel.adpeTotal.value) }} {{ panel.adpeTotal.unit }}</span></div>
    </div>
    <div class="asset-load-note">
      {{ panel.regime === 'MODELED' ? 'Computed with EcoLogits from modeled behaviour and prorated over the selected period.' : 'Computed with EcoLogits from output tokens measured over the selected period; not affected by the service filter above.' }}
    </div>
  </div>
</template>
