<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import MarkdownView from '@/components/MarkdownView.vue'

/** Markdown shown rendered, and edited in place: a click switches to its source, leaving it renders it again. */
const props = withDefaults(defineProps<{ label: string; placeholder?: string; maxlength?: number; disabled?: boolean }>(), {
  placeholder: 'Add a description; Markdown is supported.',
  maxlength: undefined,
  disabled: false,
})
const source = defineModel<string>({ required: true })
const editing = ref(false)
const textarea = ref<HTMLTextAreaElement | null>(null)
const rows = computed(() => Math.max(4, source.value.split('\n').length + 1))

async function edit(event?: MouseEvent): Promise<void> {
  /* Links stay links */
  if (props.disabled || (event?.target as HTMLElement | null)?.closest('a')) return
  editing.value = true
  await nextTick()
  textarea.value?.focus()
}
</script>

<template>
  <div class="markdown-editor" :class="{ editing, editable: !disabled }">
    <textarea
      v-if="editing"
      ref="textarea"
      v-model="source"
      class="markdown-editor-input"
      :rows="rows"
      :maxlength="maxlength"
      :placeholder="placeholder"
      :aria-label="label"
      @blur="editing = false"
      @keydown.esc="textarea?.blur()"
    />
    <div v-else class="markdown-editor-view" :title="disabled ? undefined : 'Click to edit'" @click="edit">
      <MarkdownView v-if="source.trim()" :source="source" />
      <p v-else class="markdown-editor-empty">{{ disabled ? 'No description.' : placeholder }}</p>
      <button v-if="!disabled" class="markdown-editor-edit" type="button" :aria-label="`Edit the ${label.toLowerCase()}`" @click.stop="edit()">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 20h9" /><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" /></svg>
      </button>
    </div>
  </div>
</template>
