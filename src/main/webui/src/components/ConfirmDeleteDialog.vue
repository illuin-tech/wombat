<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import Modal from '@/components/Modal.vue'

/** A deletion confirmed by typing the id of what is deleted. */
const props = withDefaults(defineProps<{open: boolean; title: string; expected: string; busy?: boolean; error?: string | null}>(), {
  busy: false,
  error: null,
})
const emit = defineEmits<{ close: []; confirm: [] }>()
const typed = ref('')
const matches = computed(() => typed.value === props.expected)

watch(() => props.open, open => {
  if (open) typed.value = ''
})

function confirm(): void {
  if (matches.value && !props.busy) emit('confirm')
}
</script>

<template>
  <Modal :open="open" :title="title" warning overlay-id="confirmDelete" title-id="confirmDeleteTitle" close-id="confirmDeleteClose" @close="emit('close')">
    <form class="confirm-delete" @submit.prevent="confirm">
      <p class="confirm-delete-text"><slot /></p>
      <label class="apikey-field">
        <span class="filter-label">Type <code>{{ expected }}</code> to confirm</span>
        <input v-model="typed" type="text" class="confirm-delete-input" autocomplete="off" spellcheck="false" :aria-label="`Type ${expected} to confirm`" />
      </label>
      <span v-if="error" class="apikey-error" role="alert">{{ error }}</span>
      <div class="apikey-actions">
        <button class="btn-danger confirm-delete-submit" type="submit" :disabled="!matches || busy">{{ busy ? 'Deleting…' : 'Delete' }}</button>
        <button class="btn-reset" type="button" @click="emit('close')">Cancel</button>
      </div>
    </form>
  </Modal>
</template>
