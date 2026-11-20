<script setup lang="ts">
import { computed, provide, ref, shallowRef, watch } from 'vue'
import { FAMILIES, FAMILY_ORDER } from '@/domain/assetTypeDisplay'
import { assetTypeName, displayName, type AssetInfo } from '@/domain/environment'
import type { JsonSchema, ModuleMetadata } from '@/domain/module'
import { createAsset, deleteAsset, updateAsset } from '@/services/api/assets'
import { errorText } from '@/services/api/client'
import { fetchModuleSchema } from '@/services/api/modules'
import { checkSecretKey } from '@/services/api/secrets'
import { AssetForm, AssetFormPlaceholder, defaultProperties, isSameProperties, SECRET_CHECKER_KEY, toAssetProperties, type AssetFormValidity, type AssetProperties } from '@/components/asset-forms'
import AssetTypeTag from '@/components/AssetTypeTag.vue'
import RegimePill from '@/components/RegimePill.vue'
import ConfirmDeleteDialog from '@/components/ConfirmDeleteDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import LockBadge from '@/components/LockBadge.vue'
import MarkdownEditor from '@/components/MarkdownEditor.vue'
import Modal from '@/components/Modal.vue'
import TimestampInfo from '@/components/TimestampInfo.vue'
import ToggleSwitch from '@/components/ToggleSwitch.vue'
import {
  DESCRIPTION_MAX_LENGTH,
  descriptionError,
  ID_MAX_LENGTH,
  idError,
  NAME_MAX_LENGTH,
  nameError,
} from '@/pages/environments/model/identifiers'
import { useIdFromName } from '@/pages/environments/model/useIdFromName'

/** Shows an asset, edits it when allowed, or creates one when `asset` is null. */
const props = defineProps<{
  open: boolean
  environmentId: string
  /** Null to create an asset. */
  asset: AssetInfo | null
  modules: ModuleMetadata[]
  /** Whether the user may edit assets, and the asset is not provisioned. */
  canEdit: boolean
  /** Whether the user may check secret names against the server. */
  canCheckSecrets: boolean
}>()
const emit = defineEmits<{ close: []; saved: [asset: AssetInfo]; deleted: [id: string] }>()

const creating = computed(() => props.asset === null)
const typeName = ref<string | null>(null)
const module = computed(() => props.modules.find(candidate => candidate.type.name === typeName.value) ?? null)
const schema = shallowRef<JsonSchema | null>(null)
const schemaLoading = ref(false)
const schemaError = ref<string | null>(null)

const name = ref('')
const { id, idEdited, onIdInput, resetId } = useIdFromName(name, () => creating.value)
const description = ref('')
const active = ref(true)
const properties = ref<AssetProperties>({})
const initialProperties = ref<AssetProperties>({})
const validity = ref<AssetFormValidity>({ valid: true, errors: [] })
const showErrors = ref(false)
const saving = ref(false)
const saveError = ref<string | null>(null)
const confirmingDiscard = ref(false)
const confirmingDelete = ref(false)
const deleting = ref(false)
const deleteError = ref<string | null>(null)

const readonly = computed(() => !props.canEdit)
const creatableModules = computed(() => {
  /* A disabled module accepts assets, but is not offered for new ones */
  const creatable = props.modules.filter(candidate => candidate.creatable && candidate.enabled)
  return FAMILY_ORDER
    .map(family => ({
      family,
      label: FAMILIES[family].label,
      modules: creatable
        .filter(candidate => candidate.type.family === family)
        .sort((a, b) => a.type.localName.localeCompare(b.type.localName)),
    }))
    .filter(group => group.modules.length > 0)
})
const title = computed(() => {
  if (props.asset) return displayName(props.asset)
  return module.value ? `New ${module.value.type.localName} asset` : 'New asset'
})

const idProblem = computed(() => creating.value ? idError(id.value) : null)
const nameProblem = computed(() => nameError(name.value))
const descriptionProblem = computed(() => descriptionError(description.value))
const fieldsValid = computed(() => !idProblem.value && !nameProblem.value && !descriptionProblem.value)
const dirty = computed(() => {
  if (creating.value) return typeName.value !== null
  const asset = props.asset!
  return name.value !== displayName(asset)
    || description.value !== (asset.description ?? '')
    || active.value !== !asset.disabledAt
    || !isSameProperties(properties.value, initialProperties.value)
})

/* Without the permission, secret names are not checked rather than refused */
if (props.canCheckSecrets) provide(SECRET_CHECKER_KEY, async key => (await checkSecretKey(key)).exists)

watch(() => [props.open, props.asset] as const, ([open, asset]) => {
  if (!open) return
  saveError.value = null
  deleteError.value = null
  showErrors.value = false
  confirmingDiscard.value = false
  resetId(asset?.id ?? '')
  name.value = asset ? displayName(asset) : ''
  description.value = asset?.description ?? ''
  active.value = !asset?.disabledAt
  const initial = asset ? toAssetProperties(asset) : {}
  properties.value = initial
  initialProperties.value = initial
  validity.value = { valid: true, errors: [] }
  void selectType(asset ? assetTypeName(asset) : null)
}, { immediate: true })

async function selectType(type: string | null): Promise<void> {
  typeName.value = type
  schema.value = null
  schemaError.value = null
  if (!type || !props.modules.some(candidate => candidate.type.name === type)) return
  schemaLoading.value = true
  try {
    const loaded = await fetchModuleSchema(type)
    if (typeName.value !== type) return
    if (creating.value) properties.value = defaultProperties(loaded)
    schema.value = loaded
  } catch (caught) {
    if (typeName.value === type) schemaError.value = errorText(caught)
  } finally {
    if (typeName.value === type) schemaLoading.value = false
  }
}

function close(): void {
  if (!readonly.value && dirty.value && !saving.value) confirmingDiscard.value = true
  else emit('close')
}

function discard(): void {
  confirmingDiscard.value = false
  emit('close')
}

async function save(): Promise<void> {
  if (!fieldsValid.value || !validity.value.valid) {
    showErrors.value = true
    return
  }
  saving.value = true
  saveError.value = null
  const fields = {
    name: name.value.trim(),
    description: description.value.trim() || null,
    disabled: !active.value,
    properties: properties.value,
  }
  try {
    const saved = creating.value
      ? await createAsset({ ...fields, id: id.value, environmentId: props.environmentId, type: typeName.value! })
      : await updateAsset(props.asset!.id, fields)
    initialProperties.value = properties.value
    emit('saved', saved)
  } catch (caught) {
    saveError.value = errorText(caught)
  } finally {
    saving.value = false
  }
}

async function remove(): Promise<void> {
  deleting.value = true
  deleteError.value = null
  try {
    await deleteAsset(props.asset!.id)
    confirmingDelete.value = false
    emit('deleted', props.asset!.id)
  } catch (caught) {
    deleteError.value = errorText(caught)
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <Modal :open="open" :title="title" @close="close">
    <div class="asset-editor">
      <!-- A new asset starts with its type -->
      <div v-if="creating && !typeName" class="asset-type-picker">
        <p class="page-desc">Pick the type of the asset:</p>
        <div v-for="group in creatableModules" :key="group.family" class="asset-type-group">
          <span class="card-section-label">{{ group.label }}</span>
          <div class="asset-type-options">
            <button
              v-for="candidate in group.modules"
              :key="candidate.type.name"
              class="asset-type-option"
              type="button"
              @click="selectType(candidate.type.name)"
            >
              <AssetTypeTag :family="candidate.type.family" />
              <span class="asset-type-option-name">{{ candidate.type.localName }}</span>
              <RegimePill :regime="candidate.type.regime" />
            </button>
          </div>
        </div>
      </div>

      <template v-else>
        <div class="asset-editor-head">
          <AssetTypeTag v-if="module" :family="module.type.family" />
          <code class="asset-card-id">{{ typeName ?? 'Unknown type' }}</code>
          <LockBadge v-if="asset && readonly && asset.provisioned" />
          <span v-if="module && !module.enabled" class="env-status disabled">Module disabled</span>
          <button v-if="creating" class="btn-link-inline" type="button" @click="selectType(null)">Change type</button>
        </div>

        <div class="asset-editor-fields">
          <label class="form-field">
            <span class="filter-label">Name</span>
            <input v-model="name" type="text" :disabled="readonly" :maxlength="NAME_MAX_LENGTH" />
            <span v-if="showErrors && nameProblem" class="form-error">{{ nameProblem }}</span>
          </label>
          <label v-if="creating" class="form-field">
            <span class="filter-label">Id</span>
            <input :value="id" type="text" class="mono" :maxlength="ID_MAX_LENGTH" spellcheck="false" @input="onIdInput" />
            <span v-if="(showErrors || idEdited) && idProblem" class="form-error">{{ idProblem }}</span>
            <span v-else class="form-hint">Cannot be changed once created.</span>
          </label>
          <div class="form-field asset-editor-description">
            <span class="filter-label">Description</span>
            <MarkdownEditor
              v-model="description"
              label="Description"
              placeholder="What this asset is about; Markdown is supported."
              :maxlength="DESCRIPTION_MAX_LENGTH"
              :disabled="readonly"
            />
            <span v-if="descriptionProblem" class="form-error">{{ descriptionProblem }}</span>
          </div>
          <ToggleSwitch v-model="active" label="Active" :disabled="readonly" />
        </div>

        <AssetFormPlaceholder v-if="schemaLoading" />
        <span v-else-if="schemaError" class="form-error" role="alert">{{ schemaError }}</span>
        <AssetForm
          v-else-if="schema"
          :key="`${typeName}:${asset?.id ?? 'new'}`"
          v-model="properties"
          :schema="schema"
          :readonly="readonly"
          :show-errors="!creating || showErrors"
          @validity="validity = $event"
        />
        <div v-else class="asset-editor-raw">
          <p class="form-hint">This type is unknown to the server: its properties are shown as stored.</p>
          <pre>{{ JSON.stringify(properties, null, 2) }}</pre>
        </div>

        <span v-if="saveError" class="form-error" role="alert">{{ saveError }}</span>
        <span v-if="showErrors && !validity.valid" class="form-error" role="alert">Some properties are missing or invalid.</span>

        <div class="asset-editor-footer">
          <TimestampInfo v-if="asset" :created-at="asset.createdAt" :updated-at="asset.updatedAt" />
          <span class="asset-editor-actions">
            <button v-if="asset && !readonly" class="btn-danger" type="button" @click="confirmingDelete = true">Delete</button>
            <button v-if="!readonly" class="btn-apply asset-editor-save" type="button" :disabled="!dirty || saving || !schema" @click="save">
              {{ saving ? 'Saving…' : creating ? 'Create asset' : 'Save' }}
            </button>
          </span>
        </div>
      </template>
    </div>
  </Modal>

  <ConfirmDeleteDialog
    v-if="asset"
    :open="confirmingDelete"
    :title="`Delete ${displayName(asset)}`"
    :expected="asset.id"
    :busy="deleting"
    :error="deleteError"
    @close="confirmingDelete = false"
    @confirm="remove"
  >
    The asset stops being monitored and leaves the dashboard; its past metrics are kept.
  </ConfirmDeleteDialog>

  <ConfirmDialog :open="confirmingDiscard" title="Discard the changes?" confirm-label="Discard" @close="confirmingDiscard = false" @confirm="discard">
    The changes made to this asset are not saved yet.
  </ConfirmDialog>
</template>
