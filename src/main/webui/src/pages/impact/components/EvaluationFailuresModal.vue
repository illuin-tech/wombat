<script setup lang="ts">
import type {FailedAsset} from '@/pages/impact/model/failures'
import Modal from '@/components/Modal.vue'
import EvaluationFailureList from '@/pages/impact/components/EvaluationFailureList.vue'

defineProps<{ open: boolean; failures: FailedAsset[] }>()
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <Modal :open="open" :title="`Evaluation Failures (${failures.length})`" warning @close="emit('close')">
    <p class="unrecognized-modal-desc">The following assets could not be evaluated over the selected time range. They have been left out of the results.</p>
    <EvaluationFailureList :failures="failures" />
  </Modal>
</template>
