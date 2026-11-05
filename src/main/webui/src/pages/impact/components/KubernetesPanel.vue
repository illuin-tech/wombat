<script setup lang="ts">
import type {KubernetesPanel as Panel} from '@/pages/impact/model/aggregate'
import KubernetesDetails from '@/pages/impact/components/KubernetesDetails.vue'

const props = withDefaults(defineProps<{panel: Panel; modal?: boolean}>(), {modal: false})
const emit = defineEmits<{expand: []}>()

function expand(): void {
  emit('expand')
}

function regimeLabel(regime: Panel['regime']): string {
  if (regime === 'MODELED') return 'Modeled'
  if (regime === 'MEASURED') return 'Measured'
  return 'Unknown'
}
</script>

<template>
  <KubernetesDetails v-if="props.modal" :panel="panel" />
  <div v-else class="asset-block">
    <div class="asset-head" role="button" tabindex="0" @click="expand" @keydown.enter="expand" @keydown.space.prevent="expand">
      <button type="button" class="asset-expand" title="Expand details" aria-label="Expand details" @click.stop="expand">⤢</button>
      <span class="asset-id">{{ panel.name }}</span>
      <div class="asset-tags">
        <span class="asset-type-tag kubernetes"><img class="svc-icon" src="/icons/docker.svg" alt="container"/>KUBERNETES</span>
        <span class="asset-type-tag measure">{{ regimeLabel(panel.regime) }}</span>
      </div>
      <span class="asset-count">{{ panel.serviceIds.length }} services</span>
    </div>
    <div class="asset-detail collapsed"><KubernetesDetails :panel="panel" /></div>
  </div>
</template>
