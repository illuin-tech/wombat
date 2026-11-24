<script lang="ts">
/* The open modals, innermost last: a confirmation can open over a modal, and only the innermost one answers Escape */
const openModals: symbol[] = []
</script>

<script setup lang="ts">
import {nextTick, onBeforeUnmount, ref, useId, watch} from 'vue'

const props = withDefaults(defineProps<{open: boolean; title: string; warning?: boolean}>(), {
  warning: false,
})
const emit = defineEmits<{ close: [] }>()
const closeButton = ref<HTMLButtonElement | null>(null)
/* Stacked in the order they open, not the order they were mounted: a confirmation mounted before the modal it opens over still shows above it */
const depth = ref(0)
/* Unique per instance: modals stay in the page while closed, and several can be mounted at once */
const titleId = `modal-title-${useId()}`
const self = Symbol(titleId)

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && props.open && openModals.at(-1) === self) emit('close')
}

function release(): void {
  const index = openModals.indexOf(self)
  if (index >= 0) openModals.splice(index, 1)
  document.removeEventListener('keydown', onKeydown)
  document.body.classList.toggle('modal-open', openModals.length > 0)
}

watch(() => props.open, async (open) => {
  if (open) {
    openModals.push(self)
    depth.value = openModals.length
    document.body.classList.add('modal-open')
    document.addEventListener('keydown', onKeydown)
    await nextTick()
    closeButton.value?.focus()
  } else {
    release()
  }
}, {immediate: true})

onBeforeUnmount(release)
</script>

<template>
  <Teleport to="body">
    <div class="asset-modal-overlay" :hidden="!open" :style="{zIndex: 1000 + depth}" @click.self="emit('close')">
      <div class="asset-modal" role="dialog" aria-modal="true" :aria-labelledby="titleId">
        <div class="asset-modal-head">
          <span v-if="warning" class="warning-icon-head">
            <svg width="18" height="18" viewBox="0 0 16 16" fill="none"><path d="M8 1L15.5 14H0.5L8 1Z" fill="#ea580c" stroke="#ea580c" stroke-width="1" stroke-linejoin="round"/><path d="M8 5.5V9.5" stroke="#ffffff" stroke-width="1.5" stroke-linecap="round"/><circle cx="8" cy="11.75" r="0.75" fill="#ffffff"/></svg>
          </span>
          <span :id="titleId" class="asset-modal-title">{{ title }}</span>
          <slot name="head"/>
          <button ref="closeButton" class="asset-modal-close" type="button" title="Close" aria-label="Close" @click="emit('close')">✕</button>
        </div>
        <div class="asset-modal-body"><slot/></div>
      </div>
    </div>
  </Teleport>
</template>