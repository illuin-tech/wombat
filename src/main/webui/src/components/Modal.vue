<script setup lang="ts">
import {nextTick, onBeforeUnmount, ref, watch} from 'vue'

const props = withDefaults(defineProps<{open: boolean; title: string; warning?: boolean; overlayId?: string; titleId?: string; closeId?: string}>(), {
  warning: false,
  overlayId: 'assetModal',
  titleId: 'assetModalTitle',
  closeId: 'assetModalClose',
})
const emit = defineEmits<{ close: [] }>()
const closeButton = ref<HTMLButtonElement | null>(null)

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && props.open) emit('close')
}

watch(() => props.open, async (open) => {
  document.body.classList.toggle('modal-open', open)
  if (open) {
    document.addEventListener('keydown', onKeydown)
    await nextTick()
    closeButton.value?.focus()
  } else {
    document.removeEventListener('keydown', onKeydown)
  }
}, {immediate: true})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.classList.remove('modal-open')
})
</script>

<template>
  <Teleport to="body">
    <div :id="overlayId" class="asset-modal-overlay" :hidden="!open" @click.self="emit('close')">
      <div class="asset-modal" role="dialog" aria-modal="true" :aria-labelledby="titleId">
        <div class="asset-modal-head">
          <span v-if="warning" class="warning-icon-head">
            <svg width="18" height="18" viewBox="0 0 16 16" fill="none"><path d="M8 1L15.5 14H0.5L8 1Z" fill="#ea580c" stroke="#ea580c" stroke-width="1" stroke-linejoin="round"/><path d="M8 5.5V9.5" stroke="#ffffff" stroke-width="1.5" stroke-linecap="round"/><circle cx="8" cy="11.75" r="0.75" fill="#ffffff"/></svg>
          </span>
          <span :id="titleId" class="asset-modal-title">{{ title }}</span>
          <button :id="closeId" ref="closeButton" class="asset-modal-close" type="button" title="Close" aria-label="Close" @click="emit('close')">✕</button>
        </div>
        <div class="asset-modal-body"><slot/></div>
      </div>
    </div>
  </Teleport>
</template>