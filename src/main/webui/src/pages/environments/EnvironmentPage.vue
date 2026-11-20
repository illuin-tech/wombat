<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import type { AssetInfo, AssetType, EnvironmentInfo } from '@/domain/environment'
import type { ModuleMetadata } from '@/domain/module'
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
import LockBadge from '@/components/LockBadge.vue'
import MarkdownEditor from '@/components/MarkdownEditor.vue'
import NoticeCard from '@/components/NoticeCard.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import TimestampInfo from '@/components/TimestampInfo.vue'
import ToggleSwitch from '@/components/ToggleSwitch.vue'
import AssetEditorModal from '@/pages/environments/components/AssetEditorModal.vue'
import AssetGrid from '@/pages/environments/components/AssetGrid.vue'
import DashboardLink from '@/pages/environments/components/DashboardLink.vue'
import { descriptionError, idError, nameError, slugify } from '@/pages/environments/model/identifiers'
import { useAutosave, type AutosaveStatus } from '@/pages/environments/model/useAutosave'

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

const form = reactive({ id: '', name: '', description: '', active: true })
const idEdited = ref(false)
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
const deleteBlockedReason = computed(() => assets.value.length > 0
  ? `Delete its ${assets.value.length} asset${assets.value.length === 1 ? '' : 's'} first to delete this environment`
  : null)
const typesByName = computed(() => new Map<string, AssetType>(modules.value.map(module => [module.type.name, module.type])))

const idProblem = computed(() => creating.value ? idError(form.id) : null)
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

function show(loaded: EnvironmentInfo): void {
  environment.value = loaded
  assets.value = [...loaded.assets].sort((a, b) => (a.name ?? a.id).localeCompare(b.name ?? b.id))
  form.id = loaded.id
  form.name = loaded.name ?? loaded.id
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
      Object.assign(form, { id: '', name: '', description: '', active: true })
      idEdited.value = false
      modules.value = await modulesLoaded
      return
    }
    const id = route.params.id as string
    const [loaded, loadedModules, liveEnvironments] = await Promise.all([
      fetchEnvironment(id),
      modulesLoaded,
      fetchLiveEnvironments().catch(() => []),
    ])
    modules.value = loadedModules
    live.value = liveEnvironments.some(candidate => candidate.id === id)
    show(loaded)
  } catch (caught) {
    loadError.value = errorText(caught)
  } finally {
    loading.value = false
  }
}

/* The id follows the name until it is edited */
watch(() => form.name, value => {
  if (creating.value && !idEdited.value) form.id = slugify(value)
})

function onIdInput(event: Event): void {
  idEdited.value = true
  form.id = (event.target as HTMLInputElement).value
}

async function create(): Promise<void> {
  showErrors.value = true
  if (idProblem.value || nameProblem.value || descriptionProblem.value) return
  submitting.value = true
  creationError.value = null
  try {
    const created = await createEnvironment({ id: form.id, ...fields() })
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
  assets.value = [...others, saved].sort((a, b) => (a.name ?? a.id).localeCompare(b.name ?? b.id))
  assetEditorOpen.value = false
  void refreshLive()
}

function onAssetDeleted(id: string): void {
  assets.value = assets.value.filter(asset => asset.id !== id)
  assetEditorOpen.value = false
  void refreshLive()
}

/* Changes waiting for the idle delay are saved before leaving, and a closing tab warns about them */
onBeforeRouteLeave(async () => {
  if (!creating.value && autosave.hasUnsavedChanges()) await autosave.flush()
  if (autosave.hasUnsavedChanges())
    return window.confirm('Some changes could not be saved. Leave anyway?')
  return true
})

function onBeforeUnload(event: BeforeUnloadEvent): void {
  if (!creating.value && autosave.hasUnsavedChanges()) event.preventDefault()
}

watch(() => route.fullPath, (path, previous) => {
  /* Creation replaces the route with the created environment's, which is already shown */
  if (previous && route.name === 'environment' && environment.value?.id === route.params.id) return
  void load()
})

onMounted(() => {
  window.addEventListener('beforeunload', onBeforeUnload)
  void load()
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload))
</script>

<template>
  <div class="main env-page">
    <RouterLink class="env-back" :to="{ name: 'environments' }">← Environments</RouterLink>

    <NoticeCard v-if="loadError" title="This environment could not be loaded" tone="error">{{ loadError }}</NoticeCard>

    <div v-else-if="loading && !environment && !creating" class="env-head env-head-loading">
      <SkeletonBlock width="240px" height="28px" />
      <SkeletonBlock width="360px" height="14px" />
    </div>

    <template v-else>
      <div class="env-head">
        <div class="env-head-main">
          <label class="env-title">
            <span class="visually-hidden">Name</span>
            <input
              v-if="canEditEnvironment"
              v-model="form.name"
              class="env-name-input"
              type="text"
              maxlength="255"
              :placeholder="creating ? 'New environment' : 'Name'"
            />
            <h2 v-else class="page-title">{{ form.name }}</h2>
          </label>
          <span v-if="(showErrors || !creating) && nameProblem" class="apikey-error">{{ nameProblem }}</span>

          <div class="env-id-line">
            <template v-if="creating">
              <label class="apikey-field env-id-field">
                <span class="filter-label">Id</span>
                <input :value="form.id" class="mono" type="text" maxlength="64" spellcheck="false" @input="onIdInput" />
              </label>
              <span v-if="(showErrors || idEdited) && idProblem" class="apikey-error">{{ idProblem }}</span>
              <span v-else class="apikey-hint">Used in URLs and by the API: it cannot be changed once created.</span>
            </template>
            <template v-else>
              <code class="env-row-id">{{ environment?.id }}</code>
              <LockBadge v-if="locked" />
            </template>
          </div>

          <div class="env-description">
            <MarkdownEditor
              v-model="form.description"
              label="Description"
              placeholder="What this environment is about; Markdown is supported."
              :maxlength="4096"
              :disabled="!canEditEnvironment"
            />
            <span v-if="descriptionProblem" class="apikey-error">{{ descriptionProblem }}</span>
          </div>
        </div>

        <div class="env-head-side">
          <ToggleSwitch v-model="form.active" label="Active" :disabled="!canEditEnvironment" />
          <template v-if="!creating && environment">
            <DashboardLink :environment-id="environment.id" :live="live" />
            <!-- The explanation sits on a wrapper: a disabled button gets no hover, hence no tooltip, in some browsers -->
            <span v-if="canEditEnvironment" class="env-delete-wrapper" :title="deleteBlockedReason ?? undefined">
              <button class="btn-danger env-delete" type="button" :disabled="deleteBlockedReason !== null" @click="confirmingDelete = true">Delete</button>
            </span>
          </template>
          <template v-else-if="creating">
            <button class="btn-apply" type="button" :disabled="submitting" id="createEnvironment" @click="create">{{ submitting ? 'Creating…' : 'Create environment' }}</button>
            <span v-if="creationError" class="apikey-error" role="alert">{{ creationError }}</span>
          </template>
        </div>

        <div v-if="!creating && environment" class="env-head-foot">
          <span v-if="saveStatusText" class="env-save-status" :class="autosave.status.value" role="status">{{ saveStatusText }}</span>
          <TimestampInfo class="env-timestamps" :created-at="environment.createdAt" :updated-at="environment.updatedAt" />
        </div>
      </div>

      <section v-if="!creating && environment" class="env-assets">
        <div class="chart-title">Assets</div>
        <AssetGrid :assets="assets" :types-by-name="typesByName" :locked="locked" :can-add="canEditAssets" @open="openAsset" @add="openAsset(null)" />
      </section>
      <p v-else-if="creating" class="apikey-hint">Assets can be added once the environment is created.</p>
    </template>

    <AssetEditorModal
      v-if="environment"
      :open="assetEditorOpen"
      :environment-id="environment.id"
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
      :title="`Delete ${environment.name ?? environment.id}`"
      :expected="environment.id"
      :busy="deleting"
      :error="deleteError"
      @close="confirmingDelete = false"
      @confirm="remove"
    >
      The environment leaves the dashboard; its past metrics are kept.
    </ConfirmDeleteDialog>
  </div>
</template>
