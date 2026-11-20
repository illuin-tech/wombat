<script setup lang="ts">
import { ref, useId } from 'vue'
import MarkdownEditor from '@/components/MarkdownEditor.vue'

/** Long-form Markdown notes, folded by default: they are read on demand rather than on every visit. */
withDefaults(defineProps<{ maxlength?: number; disabled?: boolean }>(), {
  maxlength: undefined,
  disabled: false,
})
const notes = defineModel<string>({ required: true })
const expanded = ref(false)
const regionId = useId()
</script>

<template>
  <section class="collapsible-notes" :class="{ expanded }">
    <button class="collapsible-notes-toggle" type="button" :aria-expanded="expanded" :aria-controls="regionId" @click="expanded = !expanded">
      <svg class="collapsible-notes-chevron" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 18 6-6-6-6" /></svg>
      <span class="filter-label">Notes</span>
      <span v-if="!expanded" class="form-hint">{{ notes.trim() ? 'Show' : 'None yet' }}</span>
    </button>
    <div v-if="expanded" :id="regionId">
      <MarkdownEditor
        v-model="notes"
        label="Notes"
        placeholder="Assumptions, sources, open questions; Markdown is supported."
        empty="No notes."
        :maxlength="maxlength"
        :disabled="disabled"
      />
    </div>
  </section>
</template>
