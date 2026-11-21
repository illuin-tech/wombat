<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { shortDeploymentId, type CoreDeploymentStatus, type JsonSchema, type ModuleInfo } from '@/domain/module'
import { errorText } from '@/services/api/client'
import { deleteModule, fetchDeployment, fetchModule, fetchModuleSchema, setModuleEnabled } from '@/services/api/modules'
import { can } from '@/services/auth/permissions'
import { useAppInfo } from '@/services/auth/session'
import AssetTypeTag from '@/components/AssetTypeTag.vue'
import ConfirmDeleteDialog from '@/components/ConfirmDeleteDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import NoticeCard from '@/components/NoticeCard.vue'
import RegimePill from '@/components/RegimePill.vue'
import SourcePill from '@/components/SourcePill.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import ToggleSwitch from '@/components/ToggleSwitch.vue'
import SchemaOutline from '@/pages/modules/components/SchemaOutline.vue'
import { moduleStates, STATE_LABELS } from '@/pages/modules/model/moduleList'
import { schemaOutline } from '@/pages/modules/model/schemaOutline'

const route = useRoute()
const router = useRouter()
const { appInfo } = useAppInfo()

const module = ref<ModuleInfo | null>(null)
const deployment = ref<CoreDeploymentStatus | null>(null)
const loading = ref(false)
const loadError = ref<string | null>(null)

const toggling = ref(false)
const toggleError = ref<string | null>(null)
const confirmingDisable = ref(false)

const confirmingDelete = ref(false)
const deleting = ref(false)
const deleteError = ref<string | null>(null)

const copied = ref(false)

const schema = ref<JsonSchema | null>(null)
const schemaError = ref<string | null>(null)
const showRawSchema = ref(false)
const outline = computed(() => schema.value ? schemaOutline(schema.value) : [])
const rawSchema = computed(() => JSON.stringify(schema.value, null, 2))

const canManage = computed(() => can(appInfo.value?.user, 'ui.modules.manage') && can(appInfo.value?.user, 'api.module.manage'))
const coreId = computed(() => deployment.value ? shortDeploymentId(deployment.value.latest.id) : '')

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const [loaded, status] = await Promise.all([fetchModule(route.params.type as string), fetchDeployment()])
    module.value = loaded
    deployment.value = status
  } catch (caught) {
    loadError.value = errorText(caught)
  } finally {
    loading.value = false
  }
  void loadSchema()
}

/* Only an instance detecting the module holds the class its schema is generated from */
async function loadSchema(): Promise<void> {
  schema.value = null
  schemaError.value = null
  if (!module.value?.assetClass) return
  try {
    schema.value = await fetchModuleSchema(module.value.type.name)
  } catch (caught) {
    schemaError.value = errorText(caught)
  }
}

function requestToggle(enabled: boolean): void {
  if (!enabled && module.value!.assetCount > 0) {
    confirmingDisable.value = true
    return
  }
  void toggle(enabled)
}

async function toggle(enabled: boolean): Promise<void> {
  confirmingDisable.value = false
  toggling.value = true
  toggleError.value = null
  try {
    module.value = await setModuleEnabled(module.value!.type.name, enabled)
  } catch (caught) {
    toggleError.value = errorText(caught)
  } finally {
    toggling.value = false
  }
}

async function remove(): Promise<void> {
  deleting.value = true
  deleteError.value = null
  try {
    await deleteModule(module.value!.type.name)
    confirmingDelete.value = false
    await router.push({ name: 'modules' })
  } catch (caught) {
    deleteError.value = errorText(caught)
  } finally {
    deleting.value = false
  }
}

async function copyName(): Promise<void> {
  await navigator.clipboard?.writeText(module.value!.type.name)
  copied.value = true
  setTimeout(() => { copied.value = false }, 1500)
}

watch(() => route.params.type, () => void load())
onMounted(load)
</script>

<template>
  <div class="main form-page">
    <RouterLink class="env-back" :to="{ name: 'modules' }">← Modules</RouterLink>

    <NoticeCard v-if="loadError" title="This module could not be loaded" tone="error">{{ loadError }}</NoticeCard>

    <div v-else-if="loading && !module" class="env-head env-head-loading">
      <SkeletonBlock width="240px" height="28px" />
      <SkeletonBlock width="360px" height="14px" />
    </div>

    <template v-else-if="module && deployment">
      <div class="env-head module-head" :class="{ inactive: module.missing }">
        <div class="env-head-main">
          <h2 class="page-title">{{ module.type.localName }}</h2>
          <div class="env-id-line">
            <code class="tech-name">{{ module.type.name }}</code>
            <button class="btn-link-inline" type="button" @click="copyName">{{ copied ? 'Copied' : 'Copy' }}</button>
          </div>
          <dl class="module-facts">
            <dt>Namespace</dt><dd><code class="tech-name">{{ module.type.namespace }}</code></dd>
            <dt>Asset class</dt><dd><code v-if="module.assetClass" class="tech-name">{{ module.assetClass }}</code><template v-else>—</template></dd>
            <dt>Family</dt><dd><AssetTypeTag :family="module.type.family" /></dd>
            <dt>Regime</dt><dd><RegimePill :regime="module.type.regime" /></dd>
            <dt>Source</dt><dd><SourcePill v-if="module.source" :source="module.source" /><template v-else>Unknown</template></dd>
            <dt>Assets</dt><dd>{{ module.assetCount }}</dd>
          </dl>
        </div>

        <div class="env-head-side module-state-block">
          <span class="module-states">
            <span v-for="state in moduleStates(module)" :key="state" class="module-state" :class="state">{{ STATE_LABELS[state] }}</span>
          </span>
          <span class="module-core-line">
            {{ module.deployed ? 'Running' : 'Not running' }} in core <code class="core-id" :title="deployment.latest.id">{{ coreId }}</code> on this instance
          </span>
          <ToggleSwitch
            v-if="canManage && !module.missing"
            :model-value="module.enabled"
            label="Enabled"
            :disabled="toggling"
            @update:model-value="requestToggle"
          />
          <button v-if="canManage && module.missing" class="btn-danger" type="button" @click="confirmingDelete = true">Delete</button>
        </div>
      </div>

      <span v-if="toggleError" class="form-error" role="alert">{{ toggleError }}</span>
      <NoticeCard v-if="module.missing" title="No instance detects this module any more">
        Its extension jar was removed, or no instance running it is alive. Its assets are kept, but not monitored nor
        evaluated. Should its jar come back, the module keeps its state.
      </NoticeCard>
      <NoticeCard v-else-if="module.pending" title="Not deployed yet">
        {{ module.enabled ? 'Offered' : 'Hidden' }} in the asset pickers now. A core redeployment is required for
        monitoring and evaluations to take it into account, even across restarts:
        <RouterLink :to="{ name: 'modules' }">redeploy from the Modules page</RouterLink>.
      </NoticeCard>

      <section v-if="!module.missing" class="table-card module-schema">
        <div class="table-head table-head-controls">
          <span>Asset properties</span>
          <button v-if="schema" class="btn-link-inline" type="button" @click="showRawSchema = !showRawSchema">
            {{ showRawSchema ? 'Show the outline' : 'Show the raw JSON schema' }}
          </button>
        </div>
        <div class="module-schema-body">
          <p v-if="!module.assetClass" class="form-hint">This instance does not detect the module: its schema is not available here.</p>
          <span v-else-if="schemaError" class="form-error" role="alert">{{ schemaError }}</span>
          <SkeletonBlock v-else-if="!schema" width="320px" height="14px" />
          <pre v-else-if="showRawSchema" class="schema-raw">{{ rawSchema }}</pre>
          <SchemaOutline v-else :properties="outline" />
        </div>
      </section>
    </template>

    <ConfirmDialog
      :open="confirmingDisable"
      title="Disable this module"
      confirm-label="Disable"
      @close="confirmingDisable = false"
      @confirm="toggle(false)"
    >
      {{ module?.assetCount }} asset{{ module?.assetCount === 1 ? ' uses' : 's use' }} this module. They are kept, but no
      longer monitored nor evaluated once the core is redeployed.
    </ConfirmDialog>

    <ConfirmDeleteDialog
      :open="confirmingDelete"
      title="Delete a missing module"
      :expected="module?.type.name ?? ''"
      :busy="deleting"
      :error="deleteError"
      @close="confirmingDelete = false"
      @confirm="remove"
    >
      Deleting it forgets its state: should its jar come back, it is registered again, enabled.
    </ConfirmDeleteDialog>
  </div>
</template>
