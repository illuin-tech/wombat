<script setup lang="ts">
import Modal from '@/components/Modal.vue'

/** A confirmation of an action that loses something, e.g. unsaved changes. */
withDefaults(defineProps<{ open: boolean; title: string; confirmLabel?: string; cancelLabel?: string }>(), {
  confirmLabel: 'Confirm',
  cancelLabel: 'Cancel',
})
const emit = defineEmits<{ close: []; confirm: [] }>()
</script>

<template>
  <Modal :open="open" :title="title" warning @close="emit('close')">
    <form class="confirm-dialog" @submit.prevent="emit('confirm')">
      <p class="confirm-dialog-text"><slot /></p>
      <div class="form-actions">
        <button class="btn-danger confirm-dialog-submit" type="submit">{{ confirmLabel }}</button>
        <button class="btn-reset confirm-dialog-cancel" type="button" @click="emit('close')">{{ cancelLabel }}</button>
      </div>
    </form>
  </Modal>
</template>
