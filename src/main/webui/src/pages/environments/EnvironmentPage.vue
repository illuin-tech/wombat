<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { byDisplayName, displayName, type AssetInfo, type AssetType, type EnvironmentInfo } from '@/domain/environment'
import type { ModuleMetadata } from '@/domain/module'
import { createAsset, deleteAsset, updateAsset } from '@/services/api/assets'
import { errorText } from '@/services/api/client'
import {
  createEnvironment,
  deleteEnvironment,
  fetchEnvironment,
  fetchLiveEnvironments,
  updateEnvironment,
  type EnvironmentFields,
} from '@/services/api/environments'
import { fetchModules } from '@/services/api/modules'
import { can } from '@/services/auth/permissions'
import { useAppInfo } from '@/services/auth/session'
import ConfirmDeleteDialog from '@/components/ConfirmDeleteDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import LockBadge from '@/components/LockBadge.vue'
import MarkdownEditor from '@/components/MarkdownEditor.vue'
import NoticeCard from '@/components/NoticeCard.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import TimestampInfo from '@/components/TimestampInfo.vue'
import ToggleSwitch from '@/components/ToggleSwitch.vue'
import AssetEditorModal, { type AssetEditorAdapter } from '@/components/AssetEditorModal.vue'
import AssetGrid from '@/components/AssetGrid.vue'
import DashboardLink from '@/pages/environments/components/DashboardLink.vue'
import {
  DESCRIPTION_MAX_LENGTH,
  descriptionError,
  ID_MAX_LENGTH,
  idError,
  NAME_MAX_LENGTH,
  nameError,
} from '@/domain/identifiers'
import { useIdFromName } from '@/services/tools/useIdFromName'
import { useAutosave } from '@/services/tools/useAutosave'
import { autosaveStatusText, useAutosaveGuard } from '@/services/tools/useAutosaveGuard'

const route = useRoute()
const router = useRouter()
const { appInfo } = useAppInfo()

const creating = computed(() => route.name === 'environment-create')
const environment = shallowRef<EnvironmentInfo | null>(null)
const assets = ref<AssetInfo[]>([])
const modules = ref<ModuleMetadata[]>([])
const live = ref(false)
const loading = ref(false)
const loadError = ref<string | null>(null)

const form = reactive({ name: '', description: '', active: true })
const { id, idEdited, onIdInput, resetId } = useIdFromName(() => form.name, () => creating.value)
const creationError = ref<string | null>(null)
const submitting = ref(false)
const showErrors = ref(false)

const confirmingDelete = ref(false)
const deleting = ref(false)
const deleteError = ref<string | null>(null)

const editedAsset = ref<AssetInfo | null>(null)
const assetEditorOpen = ref(false)

const user = computed(() => appInfo.value?.user)
const locked = computed(() => environment.value?.provisioned ?? false)
const canEditEnvironment = computed(() => creating.value || (can(user.value, 'api.environment.write') && !locked.value))
const canEditAssets = computed(() => can(user.value, 'api.asset.write') && !locked.value && !creating.value)
/* The asset editor saves into this environment */
const assetAdapter = computed<AssetEditorAdapter | null>(() => {
  const environmentId = environment.value?.id
  if (!environmentId) return null
  return {
    create: body => createAsset({ ...body, environmentId }),
    update: updateAsset,
    remove: deleteAsset,
    recordFields: new Set(),
    activeToggle: true,
    notes: false,
    deleteNotice: 'The asset stops being monitored and leaves the dashboard; its past metrics are kept.',
  }
})
const deleteBlockedReason = computed(() => assets.value.length > 0
  ? `Delete its ${assets.value.length} asset${assets.value.length === 1 ? '' : 's'} first to delete this environment`
  : null)
const typesByName = computed(() => new Map<string, AssetType>(modules.value.map(module => [module.type.name, module.type])))

const idProblem = computed(() => creating.value ? idError(id.value) : null)
const nameProblem = computed(() => nameError(form.name))
const descriptionProblem = computed(() => descriptionError(form.description))

function fields(): EnvironmentFields {
  return { name: form.name.trim(), description: form.description.trim() || null, disabled: !form.active }
}

/* The dashboard shows enabled environments with recognized assets: whether it does changes with them */
async function refreshLive(): Promise<void> {
  const id = environment.value?.id
  if (!id) return
  const liveEnvironments = await fetchLiveEnvironments().catch(() => null)
  if (liveEnvironments && environment.value?.id === id) live.value = liveEnvironments.some(candidate => candidate.id === id)
}

const autosave = useAutosave(fields, async value => {
  const saved = await updateEnvironment(environment.value!.id, value)
  environment.value = { ...environment.value!, ...saved, assets: environment.value!.assets }
  void refreshLive()
}, { isValid: value => !nameError(value.name) && !descriptionError(value.description ?? '') })

const saveStatusText = computed(() => autosaveStatusText(autosave))
/* Changes waiting for the idle delay are saved before leaving, and a closing tab warns about them */
const guard = useAutosaveGuard(autosave, () => !creating.value)

function show(loaded: EnvironmentInfo): void {
  environment.value = loaded
  assets.value = [...loaded.assets].sort(byDisplayName)
  resetId(loaded.id)
  form.name = displayName(loaded)
  form.description = loaded.description ?? ''
  form.active = !loaded.disabledAt
  autosave.reset(fields())
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const modulesLoaded = fetchModules()
    if (creating.value) {
      environment.value = null
      Object.assign(form, { name: '', description: '', active: true })
      resetId()
      modules.value = await modulesLoaded
      return
    }
    const environmentId = route.params.id as string
    const [loaded, loadedModules, liveEnvironments] = await Promise.all([
      fetchEnvironment(environmentId),
      modulesLoaded,
      fetchLiveEnvironments().catch(() => []),
    ])
    modules.value = loadedModules
    live.value = liveEnvironments.some(candidate => candidate.id === environmentId)
    show(loaded)
  } catch (caught) {
    loadError.value = errorText(caught)
  } finally {
    loading.value = false
  }
}

async function create(): Promise<void> {
  showErrors.value = true
  if (idProblem.value || nameProblem.value || descriptionProblem.value) return
  submitting.value = true
  creationError.value = null
  try {
    const created = await createEnvironment({ id: id.value, ...fields() })
    show(created)
    await router.replace({ name: 'environment', params: { id: created.id } })
  } catch (caught) {
    creationError.value = errorText(caught)
  } finally {
    submitting.value = false
  }
}

async function remove(): Promise<void> {
  deleting.value = true
  deleteError.value = null
  try {
    await deleteEnvironment(environment.value!.id)
    autosave.reset(fields())
    confirmingDelete.value = false
    await router.push({ name: 'environments' })
  } catch (caught) {
    deleteError.value = errorText(caught)
  } finally {
    deleting.value = false
  }
}

function openAsset(asset: AssetInfo | null): void {
  editedAsset.value = asset
  assetEditorOpen.value = true
}

function onAssetSaved(saved: AssetInfo): void {
  const others = assets.value.filter(asset => asset.id !== saved.id)
  assets.value = [...others, saved].sort(byDisplayName)
  assetEditorOpen.value = false
  void refreshLive()
}

function onAssetDeleted(id: string): void {
  assets.value = assets.value.filter(asset => asset.id !== id)
  assetEditorOpen.value = false
  void refreshLive()
}

watch(() => route.fullPath, (path, previous) => {
  /* Creation replaces the route with the created environment's, which is already shown */
  if (previous && route.name === 'environment' && environment.value?.id === route.params.id) return
  void load()
})

onMounted(() => void load())
</script>

<template>
  <div class="main form-page">
    <RouterLink class="back-link" :to="{ name: 'environments' }">← Environments</RouterLink>

    <NoticeCard v-if="loadError" title="This environment could not be loaded" tone="error">{{ loadError }}</NoticeCard>

    <div v-else-if="loading && !environment && !creating" class="record-head record-head-loading">
      <SkeletonBlock width="240px" height="28px" />
      <SkeletonBlock width="360px" height="14px" />
    </div>

    <template v-else>
      <div class="record-head">
        <div class="record-head-main">
          <label class="record-title">
            <span class="visually-hidden">Name</span>
            <input
              v-if="canEditEnvironment"
              v-model="form.name"
              class="record-name-input"
              type="text"
              :maxlength="NAME_MAX_LENGTH"
              :placeholder="creating ? 'New environment' : 'Name'"
            />
            <h2 v-else class="page-title">{{ form.name }}</h2>
          </label>
          <span v-if="(showErrors || !creating) && nameProblem" class="form-error">{{ nameProblem }}</span>

          <div class="record-id-line">
            <template v-if="creating">
              <label class="form-field record-id-field">
                <span class="filter-label">Id</span>
                <input :value="id" class="mono" type="text" :maxlength="ID_MAX_LENGTH" spellcheck="false" @input="onIdInput" />
              </label>
              <span v-if="(showErrors || idEdited) && idProblem" class="form-error">{{ idProblem }}</span>
              <span v-else class="form-hint">Used in URLs and by the API: it cannot be changed once created.</span>
            </template>
            <template v-else>
              <code class="record-id">{{ environment?.id }}</code>
              <LockBadge v-if="locked" />
            </template>
          </div>

          <div class="record-description">
            <MarkdownEditor
              v-model="form.description"
              label="Description"
              placeholder="What this environment is about; Markdown is supported."
              :maxlength="DESCRIPTION_MAX_LENGTH"
              :disabled="!canEditEnvironment"
            />
            <span v-if="descriptionProblem" class="form-error">{{ descriptionProblem }}</span>
          </div>
        </div>

        <div class="record-head-side">
          <ToggleSwitch v-model="form.active" label="Active" :disabled="!canEditEnvironment" />
          <template v-if="!creating && environment">
            <DashboardLink :environment-id="environment.id" :live="live" />
            <!-- The explanation sits on a wrapper: a disabled button gets no hover, hence no tooltip, in some browsers -->
            <span v-if="canEditEnvironment" class="record-delete-wrapper" :title="deleteBlockedReason ?? undefined">
              <button class="btn-danger record-delete" type="button" :disabled="deleteBlockedReason !== null" @click="confirmingDelete = true">Delete</button>
            </span>
          </template>
          <template v-else-if="creating">
            <button class="btn-apply" type="button" :disabled="submitting" id="createEnvironment" @click="create">{{ submitting ? 'Creating…' : 'Create environment' }}</button>
            <span v-if="creationError" class="form-error" role="alert">{{ creationError }}</span>
          </template>
        </div>

        <div v-if="!creating && environment" class="record-head-foot">
          <span v-if="saveStatusText" class="save-status" :class="autosave.status.value" role="status">{{ saveStatusText }}</span>
          <TimestampInfo class="record-timestamps" :created-at="environment.createdAt" :updated-at="environment.updatedAt" />
        </div>
      </div>

      <section v-if="!creating && environment" class="record-assets">
        <div class="chart-title">Assets</div>
        <AssetGrid
          :assets="assets"
          :types-by-name="typesByName"
          :locked="locked"
          :can-add="canEditAssets"
          empty-text="No assets in this environment."
          @open="openAsset"
          @add="openAsset(null)"
        />
      </section>
      <p v-else-if="creating" class="form-hint">Assets can be added once the environment is created.</p>
    </template>

    <AssetEditorModal
      v-if="assetAdapter"
      :open="assetEditorOpen"
      :adapter="assetAdapter"
      :asset="editedAsset"
      :modules="modules"
      :can-edit="canEditAssets && !editedAsset?.provisioned"
      :can-check-secrets="can(user, 'api.secret.read')"
      @close="assetEditorOpen = false"
      @saved="onAssetSaved"
      @deleted="onAssetDeleted"
    />

    <ConfirmDeleteDialog
      v-if="environment"
      :open="confirmingDelete"
      :title="`Delete ${displayName(environment)}`"
      :expected="environment.id"
      :busy="deleting"
      :error="deleteError"
      @close="confirmingDelete = false"
      @confirm="remove"
    >
      The environment leaves the dashboard; its past metrics are kept.
    </ConfirmDeleteDialog>

    <ConfirmDialog v-if="guard.leaving.value" open title="Leave without saving?" confirm-label="Leave" @close="guard.stay()" @confirm="guard.leave()">
      Some changes could not be saved.
    </ConfirmDialog>
  </div>
</template>
