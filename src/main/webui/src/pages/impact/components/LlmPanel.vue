<script setup lang="ts">
import type {LLMPanel as Panel} from '@/pages/impact/model/aggregate'
import LlmDetails from '@/pages/impact/components/LlmDetails.vue'

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
  <LlmDetails v-if="props.modal" :panel="panel" />
  <div v-else class="asset-block">
    <div class="asset-head" role="button" tabindex="0" @click="expand" @keydown.enter="expand" @keydown.space.prevent="expand">
      <button type="button" class="asset-expand" title="Expand details" aria-label="Expand details" @click.stop="expand">⤢</button>
      <span class="asset-id">{{ panel.name }}</span>
      <div class="asset-tags">
        <span class="asset-type-tag llm"><img class="svc-icon" src="/icons/llm.svg" alt="llm"/>LLM</span>
        <span class="asset-type-tag measure">{{ regimeLabel(panel.regime) }}</span>
      </div>
      <span class="asset-count">{{ panel.serviceIds.length }} {{ panel.serviceIds.length === 1 ? 'service' : 'services' }}</span>
    </div>
    <div class="asset-detail collapsed"><LlmDetails :panel="panel" /></div>
  </div>
</template>
