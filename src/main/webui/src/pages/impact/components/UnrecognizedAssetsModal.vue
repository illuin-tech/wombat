<script setup lang="ts">
import type {AssetInfo} from '@/domain/environment'
import Modal from '@/components/Modal.vue'

defineProps<{ open: boolean; assets: AssetInfo[] }>()
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <Modal :open="open" :title="`Unrecognized Assets (${assets.length})`" overlay-id="unrecognizedAssetsModal" title-id="unrecognizedModalTitle" close-id="unrecognizedModalClose" warning @close="emit('close')">
    <p class="unrecognized-modal-desc">The following assets are defined in the database with unknown or unregistered module types. They have been omitted from active metrics evaluation.</p>
    <div class="unrecognized-assets-list">
      <div v-for="asset in assets" :key="asset.id" class="unrecognized-asset-item">
        <div class="unrecognized-asset-name">{{ asset.name }}</div>
        <div class="unrecognized-asset-meta"><span class="unrecognized-meta-label">ID:</span> <code class="unrecognized-code">{{ asset.id }}</code> <span
            class="unrecognized-meta-label">Module / Type:</span> <code class="unrecognized-code">{{ asset.type }}</code></div>
      </div>
    </div>
  </Modal>
</template>
