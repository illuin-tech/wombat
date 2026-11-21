<script setup lang="ts">
export type CreationSource = 'existing' | 'scratch'

/** The first step of a creation, as two large panes: from something that exists, or from scratch. */
defineProps<{ existingTitle: string; existingSubtitle: string; scratchTitle: string; scratchSubtitle: string }>()
const emit = defineEmits<{ choose: [source: CreationSource] }>()
</script>

<template>
  <div class="creation-choice">
    <button class="creation-choice-pane" type="button" data-source="existing" @click="emit('choose', 'existing')">
      <svg class="creation-choice-icon" width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <rect x="9" y="9" width="12" height="12" rx="2" /><path d="M5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1" />
      </svg>
      <span class="creation-choice-title">{{ existingTitle }}</span>
      <span class="creation-choice-subtitle">{{ existingSubtitle }}</span>
    </button>
    <button class="creation-choice-pane" type="button" data-source="scratch" @click="emit('choose', 'scratch')">
      <svg class="creation-choice-icon" width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8Z" /><path d="M14 3v5h5" /><path d="M12 11v6" /><path d="M9 14h6" />
      </svg>
      <span class="creation-choice-title">{{ scratchTitle }}</span>
      <span class="creation-choice-subtitle">{{ scratchSubtitle }}</span>
    </button>
  </div>
</template>
