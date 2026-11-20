<script setup lang="ts">
import type {KubernetesPanel as Panel} from '@/pages/impact/model/aggregate'
import KubernetesDetails from '@/pages/impact/components/KubernetesDetails.vue'
import AssetTypeTag from '@/components/AssetTypeTag.vue'
import RegimePill from '@/components/RegimePill.vue'

const props = withDefaults(defineProps<{panel: Panel; modal?: boolean}>(), {modal: false})
const emit = defineEmits<{expand: []}>()

function expand(): void {
  emit('expand')
}
</script>

<template>
  <KubernetesDetails v-if="props.modal" :panel="panel" />
  <div v-else class="asset-block">
    <div class="asset-head" role="button" tabindex="0" @click="expand" @keydown.enter="expand" @keydown.space.prevent="expand">
      <button type="button" class="asset-expand" title="Expand details" aria-label="Expand details" @click.stop="expand">⤢</button>
      <span class="asset-id">{{ panel.name }}</span>
      <div class="asset-tags">
        <AssetTypeTag family="KUBERNETES_CONTAINER" />
        <RegimePill :regime="panel.regime" />
      </div>
      <span class="asset-count">{{ panel.serviceIds.length }} services</span>
    </div>
    <div class="asset-detail collapsed"><KubernetesDetails :panel="panel" /></div>
  </div>
</template>
