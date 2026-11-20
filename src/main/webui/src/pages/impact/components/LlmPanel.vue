<script setup lang="ts">
import type {LLMPanel as Panel} from '@/pages/impact/model/aggregate'
import LlmDetails from '@/pages/impact/components/LlmDetails.vue'
import AssetTypeTag from '@/components/AssetTypeTag.vue'
import RegimePill from '@/components/RegimePill.vue'

const props = withDefaults(defineProps<{panel: Panel; modal?: boolean}>(), {modal: false})
const emit = defineEmits<{expand: []}>()

function expand(): void {
  emit('expand')
}
</script>

<template>
  <LlmDetails v-if="props.modal" :panel="panel" />
  <div v-else class="asset-block">
    <div class="asset-head" role="button" tabindex="0" @click="expand" @keydown.enter="expand" @keydown.space.prevent="expand">
      <button type="button" class="asset-expand" title="Expand details" aria-label="Expand details" @click.stop="expand">⤢</button>
      <span class="asset-id">{{ panel.name }}</span>
      <div class="asset-tags">
        <AssetTypeTag family="LLM" />
        <RegimePill :regime="panel.regime" />
      </div>
      <span class="asset-count">{{ panel.serviceIds.length }} {{ panel.serviceIds.length === 1 ? 'service' : 'services' }}</span>
    </div>
    <div class="asset-detail collapsed"><LlmDetails :panel="panel" /></div>
  </div>
</template>
