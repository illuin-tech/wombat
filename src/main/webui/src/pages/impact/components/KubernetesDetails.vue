<script setup lang="ts">
import type {KubernetesPanel} from '@/pages/impact/model/aggregate'
import {asDecimal} from '@/services/format/formatters'

defineProps<{panel: KubernetesPanel}>()
</script>

<template>
  <div class="card-section">
    <div class="card-section-label">Cluster / Namespace</div>
    <div class="namespace-list">
      <span v-for="cluster in panel.clusters" :key="`${cluster.id}-${cluster.namespace}`" class="namespace-tag">{{ cluster.id }} / {{ cluster.namespace }}</span>
    </div>
  </div>
  <div class="card-section">
    <div class="card-section-label">Services</div>
    <div class="service-tags">
      <span v-for="service in panel.serviceIds" :key="service" class="service-tag"><img class="svc-icon" src="/icons/docker.svg" alt="container"/>{{ service }}</span>
    </div>
  </div>
  <div class="params-inline">
    <div class="param-item"><span class="param-key">Provider</span><span class="param-val">{{ panel.provider }}</span></div>
    <div class="param-item"><span class="param-key">Instance type</span><span class="param-val">{{ panel.instanceType }}</span></div>
    <div class="param-item"><span class="param-key">Location</span><span class="param-val">{{ panel.location }}</span></div>
    <div class="param-item"><span class="param-key">Lifespan</span><span class="param-val">{{ asDecimal(panel.lifespan) }} h</span></div>
    <div class="param-item"><span class="param-key">vCPU</span><span class="param-val">{{ panel.specification?.vcpu }}</span></div>
    <div class="param-item"><span class="param-key">Memory</span><span class="param-val">{{ panel.specification?.memory }} GB</span></div>
  </div>
  <div class="card-section asset-load">
    <div class="card-section-label">Asset load over period</div>
    <div class="asset-load-row">
      <div class="param-item"><span class="param-key">CPU used</span><span class="param-val">{{ asDecimal(panel.load.cpuUsageCores) }} cores</span></div>
      <div class="param-item"><span class="param-key">Load factor</span><span class="param-val">{{ asDecimal(panel.load.loadPercent) }} %</span></div>
      <div class="param-item"><span class="param-key">Nodes used</span><span class="param-val">{{ panel.load.nodeCount }}</span></div>
    </div>
    <div class="asset-load-note">Load values are computed across all services of this asset and are not affected by the service filter above.</div>
  </div>
</template>
