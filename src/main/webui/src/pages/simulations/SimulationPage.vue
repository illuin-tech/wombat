<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { byDisplayName, displayName, type AssetInfo, type AssetType, type EnvironmentInfo } from '@/domain/environment'
import { DESCRIPTION_MAX_LENGTH, descriptionError, NAME_MAX_LENGTH, nameError, NOTES_MAX_LENGTH, notesError } from '@/domain/identifiers'
import type { ModuleMetadata } from '@/domain/module'
import { parentEnvironmentRemoved, type EvaluationScope, type SimulationAssetInfo, type SimulationInfo, type SimulationPeriod } from '@/domain/simulation'
import { errorText } from '@/services/api/client'
import { fetchEnvironments } from '@/services/api/environments'
import { fetchModules } from '@/services/api/modules'
import {
  createSimulationAsset,
  deleteSimulation,
  deleteSimulationAsset,
  fetchSimulation,
  fetchSimulationAsset,
  syncSimulationAsset,
  updateSimulation,
  updateSimulationAsset,
  type SimulationFields,
} from '@/services/api/simulations'
import { can } from '@/services/auth/permissions'
import { useAppInfo } from '@/services/auth/session'
import { useAutosave, type AutosaveStatus } from '@/services/tools/useAutosave'
import AssetEditorModal, { type AssetEditorAdapter } from '@/components/AssetEditorModal.vue'
import AssetGrid from '@/components/AssetGrid.vue'
import CollapsibleNotes from '@/components/CollapsibleNotes.vue'
import ConfirmDeleteDialog from '@/components/ConfirmDeleteDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import CreationChoice, { type CreationSource } from '@/components/CreationChoice.vue'
import MarkdownEditor from '@/components/MarkdownEditor.vue'
import Modal from '@/components/Modal.vue'
import NoticeCard from '@/components/NoticeCard.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import TimestampInfo from '@/components/TimestampInfo.vue'
import AssetCopyPicker from '@/pages/simulations/components/AssetCopyPicker.vue'
import SimulationResults from '@/pages/simulations/components/SimulationResults.vue'
import { useSimulationResults } from '@/pages/simulations/useSimulationResults'

const route = useRoute()
const router = useRouter()
const { appInfo } = useAppInfo()

const simulation = shallowRef<SimulationInfo | null>(null)
const assets = ref<SimulationAssetInfo[]>([])
const modules = ref<ModuleMetadata[]>([])
const loading = ref(false)
const loadError = ref<string | null>(null)

const form = reactive({ name: '', description: '', notes: '', referenceWindow: 'MONTH' as SimulationPeriod })

const confirmingDelete = ref(false)
const deleting = ref(false)
const deleteError = ref<string | null>(null)

const choosingSource = ref(false)
const picking = ref(false)
const environments = shallowRef<EnvironmentInfo[] | null>(null)
const pickError = ref<string | null>(null)
const editedAsset = ref<SimulationAssetInfo | null>(null)
const assetEditorOpen = ref(false)
const confirmingSync = ref(false)
const syncError = ref<string | null>(null)

const period = ref<SimulationPeriod>('MONTH')
const impactShown = ref(true)
const costShown = ref(true)

const user = computed(() => appInfo.value?.user)
const canEdit = computed(() => can(user.value, 'ui.simulations.manage') && can(user.value, 'api.simulation.write'))
const typesByName = computed(() => new Map<string, AssetType>(modules.value.map(module => [module.type.name, module.type])))
const nameProblem = computed(() => nameError(form.name))
const descriptionProblem = computed(() => descriptionError(form.description))
const notesProblem = computed(() => notesError(form.notes))

/* The asset editor saves into this simulation */
const assetAdapter = computed<AssetEditorAdapter | null>(() => {
  const simulationId = simulation.value?.id
  if (!simulationId) return null
  return {
    create: ({ id, type, name, description, notes, properties }) => createSimulationAsset(simulationId, { id, type, name, description, notes, properties }),
    update: (id, { name, description, notes, properties }) => updateSimulationAsset(simulationId, id, { name, description, notes: notes ?? null, properties }),
    remove: id => deleteSimulationAsset(simulationId, id),
  }
})

function fields(): SimulationFields {
  return { name: form.name.trim(), description: form.description.trim() || null, notes: form.notes.trim() || null, referenceWindow: form.referenceWindow }
}

const autosave = useAutosave(fields, async value => {
  const saved = await updateSimulation(simulation.value!.id, value)
  simulation.value = { ...saved, assets: simulation.value!.assets }
}, { isValid: value => !nameError(value.name) && !descriptionError(value.description ?? '') && !notesError(value.notes ?? '') })

/* Evaluated on the window as selected, which need not wait for its save */
const results = useSimulationResults(() => {
  if (!simulation.value) return null
  const scopes: EvaluationScope[] = [...(impactShown.value ? ['IMPACT' as const] : []), ...(costShown.value ? ['COST' as const] : [])]
  return { id: simulation.value.id, scopes, referenceWindow: form.referenceWindow }
})

/* Synchronous, so that the change made by loading is dropped by the run that follows it */
watch([() => form.referenceWindow, impactShown, costShown], () => results.schedule(), { flush: 'sync' })

const STATUS_TEXTS: Record<AutosaveStatus, string> = {
  idle: '',
  pending: 'Unsaved changes',
  saving: 'Saving…',
  saved: 'All changes saved',
  invalid: 'Not saved: fix the highlighted fields',
  error: 'Not saved',
}
const saveStatusText = computed(() => {
  const text = STATUS_TEXTS[autosave.status.value]
  return autosave.status.value === 'error' && autosave.error.value ? `${text}: ${autosave.error.value}` : text
})

function show(loaded: SimulationInfo): void {
  simulation.value = loaded
  assets.value = [...loaded.assets].sort(byDisplayName)
  Object.assign(form, { name: loaded.name, description: loaded.description ?? '', notes: loaded.notes ?? '', referenceWindow: loaded.referenceWindow })
  autosave.reset(fields())
  period.value = loaded.referenceWindow
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const [loaded, loadedModules] = await Promise.all([fetchSimulation(route.params.id as string), fetchModules()])
    modules.value = loadedModules
    show(loaded)
    void results.run()
  } catch (caught) {
    loadError.value = errorText(caught)
  } finally {
    loading.value = false
  }
}

async function remove(): Promise<void> {
  deleting.value = true
  deleteError.value = null
  try {
    await deleteSimulation(simulation.value!.id)
    autosave.reset(fields())
    confirmingDelete.value = false
    await router.push({ name: 'simulations' })
  } catch (caught) {
    deleteError.value = errorText(caught)
  } finally {
    deleting.value = false
  }
}

function chooseSource(source: CreationSource): void {
  choosingSource.value = false
  if (source === 'scratch') {
    editedAsset.value = null
    assetEditorOpen.value = true
    return
  }
  picking.value = true
  pickError.value = null
  if (!environments.value)
    fetchEnvironments()
      .then(loaded => { environments.value = [...loaded].sort(byDisplayName) })
      .catch(caught => { pickError.value = errorText(caught) })
}

/* The copy is saved at once, then opened for changes */
async function copy(parent: AssetInfo): Promise<void> {
  pickError.value = null
  try {
    const created = await createSimulationAsset(simulation.value!.id, { name: displayName(parent), parentAssetId: parent.id })
    picking.value = false
    upsert(created)
    openAsset(created)
  } catch (caught) {
    pickError.value = errorText(caught)
  }
}

/* Opened as it stands now, so that the notice tells whether its parent changed since the page loaded */
function openAsset(asset: AssetInfo): void {
  const opened = asset as SimulationAssetInfo
  editedAsset.value = opened
  syncError.value = null
  assetEditorOpen.value = true
  if (opened.parentAssetId)
    fetchSimulationAsset(simulation.value!.id, opened.id)
      .then(current => { if (editedAsset.value?.id === current.id) editedAsset.value = current })
      .catch(() => {})
}

function upsert(saved: SimulationAssetInfo): void {
  assets.value = [...assets.value.filter(asset => asset.id !== saved.id), saved].sort(byDisplayName)
  results.schedule()
}

function onAssetSaved(saved: AssetInfo): void {
  upsert(saved as SimulationAssetInfo)
  assetEditorOpen.value = false
}

function onAssetDeleted(id: string): void {
  assets.value = assets.value.filter(asset => asset.id !== id)
  assetEditorOpen.value = false
  results.schedule()
}

async function sync(): Promise<void> {
  confirmingSync.value = false
  syncError.value = null
  try {
    const synced = await syncSimulationAsset(simulation.value!.id, editedAsset.value!.id)
    upsert(synced)
    editedAsset.value = synced
  } catch (caught) {
    syncError.value = errorText(caught)
  }
}

function parentLabel(asset: AssetInfo | null): string {
  return `${asset?.parentEnvironmentId}/${asset?.parentAssetId}`
}

onBeforeRouteLeave(async () => {
  if (autosave.hasUnsavedChanges()) await autosave.flush()
  if (autosave.hasUnsavedChanges())
    return window.confirm('Some changes could not be saved. Leave anyway?')
  return true
})

function onBeforeUnload(event: BeforeUnloadEvent): void {
  if (autosave.hasUnsavedChanges()) event.preventDefault()
}

watch(() => route.params.id, (id, previous) => {
  if (id && id !== previous) void load()
})

onMounted(() => {
  window.addEventListener('beforeunload', onBeforeUnload)
  void load()
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload))
</script>

<template>
  <div class="main form-page">
    <RouterLink class="env-back" :to="{ name: 'simulations' }">← Simulations</RouterLink>

    <NoticeCard v-if="loadError" title="This simulation could not be loaded" tone="error">{{ loadError }}</NoticeCard>

    <div v-else-if="loading && !simulation" class="env-head env-head-loading">
      <SkeletonBlock width="240px" height="28px" />
      <SkeletonBlock width="360px" height="14px" />
    </div>

    <template v-else-if="simulation">
      <div class="env-head">
        <div class="env-head-main">
          <label class="env-title">
            <span class="visually-hidden">Name</span>
            <input v-if="canEdit" v-model="form.name" class="env-name-input" type="text" :maxlength="NAME_MAX_LENGTH" placeholder="Name" />
            <h2 v-else class="page-title">{{ form.name }}</h2>
          </label>
          <span v-if="nameProblem" class="form-error">{{ nameProblem }}</span>

          <div class="env-id-line">
            <code class="env-row-id">{{ simulation.id }}</code>
            <span v-if="parentEnvironmentRemoved(simulation)" class="sim-parent removed">Source environment removed</span>
            <RouterLink
              v-else-if="simulation.parentEnvironmentId"
              class="sim-parent"
              :to="{ name: 'environment', params: { id: simulation.parentEnvironmentId } }"
            >From {{ simulation.parentEnvironmentName }}</RouterLink>
          </div>

          <div class="env-description">
            <MarkdownEditor
              v-model="form.description"
              label="Description"
              placeholder="What this simulation estimates; Markdown is supported."
              :maxlength="DESCRIPTION_MAX_LENGTH"
              :disabled="!canEdit"
            />
            <span v-if="descriptionProblem" class="form-error">{{ descriptionProblem }}</span>
          </div>
          <CollapsibleNotes v-model="form.notes" :maxlength="NOTES_MAX_LENGTH" :disabled="!canEdit" open />
          <span v-if="notesProblem" class="form-error">{{ notesProblem }}</span>
        </div>

        <div class="env-head-side">
          <button v-if="canEdit" class="btn-danger env-delete" type="button" @click="confirmingDelete = true">Delete</button>
        </div>

        <div class="env-head-foot">
          <span v-if="saveStatusText" class="env-save-status" :class="autosave.status.value" role="status">{{ saveStatusText }}</span>
          <TimestampInfo class="env-timestamps" :created-at="simulation.createdAt" :updated-at="simulation.updatedAt" />
        </div>
      </div>

      <section class="env-assets">
        <div class="chart-title">Assets</div>
        <AssetGrid
          :assets="assets"
          :types-by-name="typesByName"
          :can-add="canEdit"
          empty-text="No assets in this simulation."
          @open="openAsset"
          @add="choosingSource = true"
        >
          <template #badges="{ asset }">
            <template v-if="asset.parentAssetId">
              <span v-if="asset.parentStatus === 'parent-removed'" class="sim-badge removed">Original removed</span>
              <span v-else class="sim-badge copy" :title="`Copied from ${parentLabel(asset)}`">
                Copy{{ asset.parentStatus === 'diverged' ? ', changed' : '' }}
              </span>
            </template>
          </template>
        </AssetGrid>
      </section>

      <SimulationResults
        v-model:period="period"
        v-model:reference-window="form.referenceWindow"
        v-model:impact="impactShown"
        v-model:cost="costShown"
        :evaluation="results.evaluation.value"
        :error="results.error.value"
        :computing="results.computing.value"
        :assets="assets"
        :types-by-name="typesByName"
        :can-edit="canEdit"
      />
    </template>

    <Modal :open="choosingSource" title="Add an asset" @close="choosingSource = false">
      <CreationChoice
        existing-title="From an existing asset"
        existing-subtitle="Copy an asset of any environment, then change the copy freely."
        scratch-title="From scratch"
        scratch-subtitle="Pick a type, then describe the asset."
        @choose="chooseSource"
      />
    </Modal>

    <AssetCopyPicker :open="picking" :environments="environments" :modules="modules" :error="pickError" @close="picking = false" @pick="copy" />

    <AssetEditorModal
      v-if="assetAdapter"
      :open="assetEditorOpen"
      :adapter="assetAdapter"
      :asset="editedAsset"
      :modules="modules"
      :can-edit="canEdit"
      :can-check-secrets="can(user, 'api.secret.read')"
      :active-toggle="false"
      with-notes
      delete-notice="The asset leaves the simulation; the asset it was copied from, if any, is left as it is."
      @close="assetEditorOpen = false"
      @saved="onAssetSaved"
      @deleted="onAssetDeleted"
    >
      <template #notice>
        <NoticeCard v-if="editedAsset?.parentStatus === 'diverged'" title="This copy and its source have diverged" tone="warning">
          This copy or {{ parentLabel(editedAsset) }} was changed since the copy was created.
          <button v-if="canEdit" class="btn-link-inline sim-sync" type="button" @click="confirmingSync = true">Sync with the original</button>
          <span v-if="syncError" class="form-error" role="alert">{{ syncError }}</span>
        </NoticeCard>
        <NoticeCard v-else-if="editedAsset?.parentStatus === 'parent-removed'" title="The original asset was deleted">
          This copy is kept as it is, and can no longer be synced.
        </NoticeCard>
      </template>
    </AssetEditorModal>

    <ConfirmDialog :open="confirmingSync" title="Sync with the original?" confirm-label="Sync" @close="confirmingSync = false" @confirm="sync">
      The copy takes the original asset's current properties, and loses the changes made to it.
    </ConfirmDialog>

    <ConfirmDeleteDialog
      v-if="simulation"
      :open="confirmingDelete"
      :title="`Delete ${simulation.name}`"
      :expected="simulation.id"
      :busy="deleting"
      :error="deleteError"
      @close="confirmingDelete = false"
      @confirm="remove"
    >
      The simulation and its assets are deleted for good. The assets they were copied from are left as they are.
    </ConfirmDeleteDialog>
  </div>
</template>
