<script setup lang="ts">
import type {ServiceActivity} from '@/pages/impact/model/serviceActivity'
import {asDecimal, asPercent} from '@/services/format/formatters'

defineProps<{activity: ServiceActivity}>()
</script>

<template>
  <div class="service-activity">
    <div class="card-section-label">Activity over period</div>
    <div v-if="activity.kind === 'llm'" class="service-activity-row">
      <div v-if="activity.model" class="param-item"><span class="param-key">Model</span><span class="param-val">{{ activity.model }}</span></div>
      <div v-if="activity.provider" class="param-item"><span class="param-key">Provider</span><span class="param-val">{{ activity.provider }}</span></div>
      <div v-if="activity.location" class="param-item"><span class="param-key">Location</span><span class="param-val">{{ activity.location }}</span></div>
      <div class="param-item"><span class="param-key">Output tokens</span><span class="param-val">{{ asDecimal(activity.outputTokenCount) }}</span></div>
      <div class="param-item"><span class="param-key">Output tokens / day</span><span class="param-val">{{ asDecimal(activity.outputTokensPerDay) }}</span></div>
      <div class="param-item"><span class="param-key">Requests</span><span class="param-val">{{ asDecimal(activity.requestCount) }}</span></div>
      <div class="param-item"><span class="param-key">Requests / day</span><span class="param-val">{{ asDecimal(activity.requestsPerDay) }}</span></div>
      <div class="param-item"><span class="param-key">Output tokens / request</span><span class="param-val">{{ asDecimal(activity.outputTokensPerRequest) }}</span></div>
    </div>
    <div v-else class="service-activity-row">
      <div v-if="activity.location" class="param-item"><span class="param-key">Cluster / Namespace</span><span class="param-val">{{ activity.location.id }} / {{ activity.location.namespace }}</span></div>
      <div class="param-item" title="The service's part of its asset's CPU time"><span class="param-key">CPU share</span><span class="param-val">{{ asPercent(activity.cpuShare) }}</span></div>
      <div class="param-item"><span class="param-key">Mean CPU used</span><span class="param-val">{{ activity.cpuUsageCores === null ? '—' : `${asDecimal(activity.cpuUsageCores)} cores` }}</span></div>
    </div>
  </div>
</template>
