<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { byDisplayName, displayName, type EnvironmentInfo } from '@/domain/environment'
import { DESCRIPTION_MAX_LENGTH, descriptionError, ID_MAX_LENGTH, idError, NAME_MAX_LENGTH, nameError } from '@/domain/identifiers'
import type { ModuleMetadata } from '@/domain/module'
import { errorText } from '@/services/api/client'
import { fetchEnvironments } from '@/services/api/environments'
import { fetchModules } from '@/services/api/modules'
import { createSimulation } from '@/services/api/simulations'
import { useIdFromName } from '@/services/tools/useIdFromName'
import CreationChoice, { type CreationSource } from '@/components/CreationChoice.vue'
import MarkdownEditor from '@/components/MarkdownEditor.vue'
import NoticeCard from '@/components/NoticeCard.vue'
import { copyCandidates } from '@/pages/simulations/model/copyCandidates'

const router = useRouter()

const source = ref<CreationSource | null>(null)
const environments = shallowRef<EnvironmentInfo[]>([])
const modules = shallowRef<ModuleMetadata[]>([])
const loadError = ref<string | null>(null)
const environmentId = ref('')
const picked = ref<string[]>([])

const name = ref('')
const { id, idEdited, onIdInput } = useIdFromName(name, () => true)
const description = ref('')
const showErrors = ref(false)
const submitting = ref(false)
const creationError = ref<string | null>(null)

const environment = computed(() => environments.value.find(candidate => candidate.id === environmentId.value) ?? null)
const candidates = computed(() => environment.value ? copyCandidates(environment.value.assets, modules.value) : [])
const idProblem = computed(() => idError(id.value))
const nameProblem = computed(() => nameError(name.value))
const descriptionProblem = computed(() => descriptionError(description.value))
const sourceProblem = computed(() => source.value === 'existing' && !environment.value ? 'Pick the environment to copy.' : null)

/* Picking an environment picks its assets, and names the simulation after it until a name is typed */
watch(environment, (current, previous) => {
  picked.value = candidates.value.filter(candidate => candidate.pickedByDefault).map(candidate => candidate.asset.id)
  if (current && (!name.value || name.value === (previous && `${displayName(previous)} what-if`)))
    name.value = `${displayName(current)} what-if`
})

async function load(): Promise<void> {
  try {
    const [loadedEnvironments, loadedModules] = await Promise.all([fetchEnvironments(), fetchModules()])
    environments.value = [...loadedEnvironments].sort(byDisplayName)
    modules.value = loadedModules
  } catch (caught) {
    loadError.value = errorText(caught)
  }
}

async function create(): Promise<void> {
  showErrors.value = true
  if (idProblem.value || nameProblem.value || descriptionProblem.value || sourceProblem.value) return
  submitting.value = true
  creationError.value = null
  try {
    const copied = source.value === 'existing' ? { sourceEnvironmentId: environmentId.value, assetIds: picked.value } : {}
    const created = await createSimulation({ id: id.value, name: name.value.trim(), description: description.value.trim() || null, ...copied })
    await router.replace({ name: 'simulation', params: { id: created.id } })
  } catch (caught) {
    creationError.value = errorText(caught)
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="main form-page">
    <RouterLink class="back-link" :to="{ name: 'simulations' }">← Simulations</RouterLink>
    <h2 class="page-title">New simulation</h2>

    <CreationChoice
      v-if="!source"
      existing-title="From an existing environment"
      existing-subtitle="Copy the assets you pick, then change the copies freely."
      scratch-title="From scratch"
      scratch-subtitle="Start empty, then add copies of assets or new definitions."
      @choose="source = $event"
    />

    <form v-else class="sim-create" @submit.prevent="create">
      <NoticeCard v-if="loadError" title="The environments could not be loaded" tone="error">{{ loadError }}</NoticeCard>

      <section v-if="source === 'existing'" class="sim-create-source">
        <label class="form-field">
          <span class="filter-label">Environment</span>
          <select v-model="environmentId" id="sourceEnvironment">
            <option value="" disabled>Pick an environment</option>
            <option v-for="candidate in environments" :key="candidate.id" :value="candidate.id">{{ displayName(candidate) }}</option>
          </select>
          <span v-if="showErrors && sourceProblem" class="form-error">{{ sourceProblem }}</span>
        </label>
        <fieldset v-if="environment" class="sim-asset-checklist">
          <legend class="filter-label">Assets to copy</legend>
          <p v-if="candidates.length === 0" class="form-hint">This environment has no assets: the simulation starts empty.</p>
          <label v-for="candidate in candidates" :key="candidate.asset.id" class="form-check" :class="{ blocked: candidate.blocked }">
            <input v-model="picked" type="checkbox" :value="candidate.asset.id" :disabled="candidate.blocked !== null" />
            <span>{{ displayName(candidate.asset) }}</span>
            <code class="asset-card-id">{{ candidate.asset.id }}</code>
            <span v-if="candidate.blocked" class="status-chip">{{ candidate.blocked }}</span>
            <span v-else-if="candidate.asset.disabledAt" class="status-chip">Inactive</span>
          </label>
        </fieldset>
      </section>

      <div class="sim-create-fields">
        <label class="form-field">
          <span class="filter-label">Name</span>
          <input v-model="name" type="text" :maxlength="NAME_MAX_LENGTH" id="simulationName" />
          <span v-if="showErrors && nameProblem" class="form-error">{{ nameProblem }}</span>
        </label>
        <label class="form-field">
          <span class="filter-label">Id</span>
          <input :value="id" class="mono" type="text" :maxlength="ID_MAX_LENGTH" spellcheck="false" id="simulationId" @input="onIdInput" />
          <span v-if="(showErrors || idEdited) && idProblem" class="form-error">{{ idProblem }}</span>
          <span v-else class="form-hint">Used in URLs and by the API: it cannot be changed once created.</span>
        </label>
        <div class="form-field sim-create-description">
          <span class="filter-label">Description</span>
          <MarkdownEditor
            v-model="description"
            label="Description"
            placeholder="What this simulation estimates; Markdown is supported."
            :maxlength="DESCRIPTION_MAX_LENGTH"
          />
          <span v-if="descriptionProblem" class="form-error">{{ descriptionProblem }}</span>
        </div>
      </div>

      <span v-if="creationError" class="form-error" role="alert">{{ creationError }}</span>
      <div class="form-actions">
        <button class="btn-apply" type="submit" :disabled="submitting" id="createSimulation">{{ submitting ? 'Creating…' : 'Create simulation' }}</button>
        <button class="btn-reset" type="button" @click="source = null">Back</button>
      </div>
    </form>
  </div>
</template>
