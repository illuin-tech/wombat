<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { SimulationInfo, SimulationSummary } from '@/domain/simulation'
import { ID_MAX_LENGTH, idError, NAME_MAX_LENGTH, nameError } from '@/domain/identifiers'
import { errorText } from '@/services/api/client'
import { duplicateSimulation } from '@/services/api/simulations'
import { useIdFromName } from '@/services/tools/useIdFromName'
import Modal from '@/components/Modal.vue'
import { duplicateName } from '@/pages/simulations/model/simulationList'

/** Asks for the name of a duplicate, its id derived from it until edited. */
const props = defineProps<{ source: SimulationSummary | null }>()
const emit = defineEmits<{ close: []; duplicated: [simulation: SimulationInfo] }>()

const name = ref('')
const { id, idEdited, onIdInput, resetId } = useIdFromName(name, () => true)
const showErrors = ref(false)
const submitting = ref(false)
const error = ref<string | null>(null)
const idProblem = computed(() => idError(id.value))
const nameProblem = computed(() => nameError(name.value))

watch(() => props.source, source => {
  if (!source) return
  resetId()
  name.value = duplicateName(source.name)
  showErrors.value = false
  error.value = null
}, { immediate: true })

async function submit(): Promise<void> {
  showErrors.value = true
  if (idProblem.value || nameProblem.value) return
  submitting.value = true
  error.value = null
  try {
    emit('duplicated', await duplicateSimulation(props.source!.id, { id: id.value, name: name.value.trim() }))
  } catch (caught) {
    error.value = errorText(caught)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Modal :open="source !== null" :title="`Duplicate ${source?.name ?? ''}`" @close="emit('close')">
    <form class="sim-duplicate" @submit.prevent="submit">
      <p class="page-desc">The duplicate holds a copy of every asset, which stay linked to the assets they were copied from.</p>
      <label class="form-field">
        <span class="filter-label">Name</span>
        <input v-model="name" type="text" :maxlength="NAME_MAX_LENGTH" />
        <span v-if="showErrors && nameProblem" class="form-error">{{ nameProblem }}</span>
      </label>
      <label class="form-field">
        <span class="filter-label">Id</span>
        <input :value="id" class="mono" type="text" :maxlength="ID_MAX_LENGTH" spellcheck="false" @input="onIdInput" />
        <span v-if="(showErrors || idEdited) && idProblem" class="form-error">{{ idProblem }}</span>
      </label>
      <span v-if="error" class="form-error" role="alert">{{ error }}</span>
      <div class="form-actions">
        <button class="btn-apply sim-duplicate-submit" type="submit" :disabled="submitting">{{ submitting ? 'Duplicating…' : 'Duplicate' }}</button>
        <button class="btn-reset" type="button" @click="emit('close')">Cancel</button>
      </div>
    </form>
  </Modal>
</template>
