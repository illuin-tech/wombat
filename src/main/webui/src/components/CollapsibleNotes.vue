<script setup lang="ts">
import { ref, useId } from 'vue'
import { useResizeObserver } from '@vueuse/core'
import MarkdownEditor from '@/components/MarkdownEditor.vue'

/* Notes taller than this, in pixels, are cut there until shown in full: the same as in components.css */
const NOTES_PREVIEW_HEIGHT = 240

/** Long-form Markdown notes, folded unless `open`: where they are read on demand rather than on every visit. */
const props = withDefaults(defineProps<{ maxlength?: number; disabled?: boolean; open?: boolean }>(), {
  maxlength: undefined,
  disabled: false,
  open: false,
})
const notes = defineModel<string>({ required: true })
const expanded = ref(props.open)
const regionId = useId()
const content = ref<HTMLElement | null>(null)
const overflowing = ref(false)
const showAll = ref(false)

/* Measured on the content, which keeps its full height while its frame is cut */
useResizeObserver(content, ([entry]) => {
  overflowing.value = entry.contentRect.height > NOTES_PREVIEW_HEIGHT
})
</script>

<template>
  <section class="collapsible-notes" :class="{ expanded }">
    <button class="collapsible-notes-toggle" type="button" :aria-expanded="expanded" :aria-controls="regionId" @click="expanded = !expanded">
      <svg class="collapsible-notes-chevron" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 18 6-6-6-6" /></svg>
      <span class="filter-label">Notes</span>
      <span v-if="!expanded" class="form-hint">{{ notes.trim() ? 'Show' : 'None yet' }}</span>
    </button>
    <div v-if="expanded" :id="regionId" class="collapsible-notes-region">
      <div class="collapsible-notes-frame" :class="{ clamped: overflowing && !showAll }">
        <div ref="content">
          <MarkdownEditor
            v-model="notes"
            label="Notes"
            placeholder="Assumptions, sources, open questions; Markdown is supported."
            empty="No notes."
            :maxlength="maxlength"
            :disabled="disabled"
          />
        </div>
      </div>
      <button v-if="overflowing" class="btn-link-inline collapsible-notes-more" type="button" @click="showAll = !showAll">
        {{ showAll ? 'Show less' : 'Show all' }}
      </button>
    </div>
  </section>
</template>
