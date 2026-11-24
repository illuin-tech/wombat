<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { CoreDeploymentStatus, ModuleInfo } from '@/domain/module'
import { errorText } from '@/services/api/client'
import { deleteModule, fetchDeployment, fetchModuleInfos } from '@/services/api/modules'
import { can } from '@/services/auth/permissions'
import { useAppInfo } from '@/services/auth/session'
import { useAsync } from '@/services/tools/useAsync'
import AssetTypeTag from '@/components/AssetTypeTag.vue'
import ConfirmDeleteDialog from '@/components/ConfirmDeleteDialog.vue'
import DataTable from '@/components/DataTable.vue'
import DropdownPicker from '@/components/DropdownPicker.vue'
import RegimePill from '@/components/RegimePill.vue'
import SourcePill from '@/components/SourcePill.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import PageHead from '@/layout/PageHead.vue'
import CoreStatus from '@/pages/modules/components/CoreStatus.vue'
import RedeployDialog from '@/pages/modules/components/RedeployDialog.vue'
import { filterModules, moduleStates, sortModules, STATE_LABELS, type ModuleState } from '@/pages/modules/model/moduleList'

interface ModulesListing {
  modules: ModuleInfo[]
  deployment: CoreDeploymentStatus
}

async function loadListing(): Promise<ModulesListing> {
  const [modules, deployment] = await Promise.all([fetchModuleInfos(), fetchDeployment()])
  return { modules: sortModules(modules), deployment }
}

const router = useRouter()
const { appInfo } = useAppInfo()
const listing = useAsync(loadListing)
const search = ref('')
const states = ref<ModuleState[]>([])
const stateOptions = (Object.keys(STATE_LABELS) as ModuleState[]).map(value => ({ value, label: STATE_LABELS[value] }))
const stateSummary = computed(() => states.value.length === 0 ? 'Any state' : states.value.map(state => STATE_LABELS[state]).join(', '))
const canManage = computed(() => can(appInfo.value?.user, 'ui.modules.manage') && can(appInfo.value?.user, 'api.module.manage'))
const shown = computed(() => filterModules(listing.data.value?.modules ?? [], search.value, states.value))
const pendingCount = computed(() => listing.data.value?.deployment.pending.length ?? 0)
const skeletonRows = [0, 1, 2]

const redeploying = ref(false)
const deleted = ref<ModuleInfo | null>(null)
const deleting = ref(false)
const deleteError = ref<string | null>(null)

function open(module: ModuleInfo): void {
  void router.push({ name: 'module', params: { type: module.type.name } })
}

function confirmDelete(module: ModuleInfo): void {
  deleted.value = module
  deleteError.value = null
}

async function remove(): Promise<void> {
  deleting.value = true
  deleteError.value = null
  try {
    await deleteModule(deleted.value!.type.name)
    deleted.value = null
    await listing.run()
  } catch (caught) {
    deleteError.value = errorText(caught)
  } finally {
    deleting.value = false
  }
}

onMounted(() => listing.run())
</script>

<template>
  <div class="main">
    <div class="list-head">
      <PageHead title="Modules">
        The asset types Wombat detects. Turning a module off hides it from the asset pickers at once; a core
        redeployment is required for monitoring and evaluations to take it into account, even across restarts.
      </PageHead>
      <button v-if="canManage && listing.data.value" class="btn-apply" type="button" id="redeployCore" @click="redeploying = true">Redeploy core</button>
    </div>

    <div v-if="listing.error.value" class="form-error" role="alert">{{ errorText(listing.error.value) }}</div>

    <template v-if="listing.data.value">
      <CoreStatus :status="listing.data.value.deployment" />
      <p v-if="pendingCount > 0" class="module-pending-banner" role="status">
        {{ pendingCount }} change{{ pendingCount === 1 ? '' : 's' }} not deployed: a core redeployment is required for
        monitoring and evaluations to take {{ pendingCount === 1 ? 'it' : 'them' }} into account.
      </p>
    </template>

    <DataTable title="Modules">
      <template #controls>
        <input v-model="search" type="search" class="search-input" placeholder="Filter by name" aria-label="Filter modules by name" />
        <DropdownPicker v-model="states" label="State" :options="stateOptions" :summary="stateSummary" picker-id="moduleStatePicker" />
      </template>
      <table class="record-table">
        <thead><tr><th>Module</th><th>Family</th><th>Source</th><th>State</th><th>Assets</th><th></th></tr></thead>
        <tbody>
          <template v-if="listing.loading.value && !listing.data.value">
            <tr v-for="row in skeletonRows" :key="row">
              <td v-for="cell in 6" :key="cell"><SkeletonBlock :width="cell === 1 ? '160px' : '64px'" height="12px" /></td>
            </tr>
          </template>
          <template v-else-if="listing.data.value">
            <tr
              v-for="module in shown"
              :key="module.type.name"
              class="record-row module-row"
              :class="{ inactive: module.missing }"
              tabindex="0"
              @click="open(module)"
              @keydown.enter="open(module)"
            >
              <td class="name">
                <span class="module-row-name">
                  {{ module.type.localName }}
                  <code class="tech-name">{{ module.type.name }}</code>
                </span>
              </td>
              <td>
                <span class="module-states">
                  <AssetTypeTag :family="module.type.family" />
                  <RegimePill v-if="module.kind === 'asset'" :regime="module.type.regime" />
                  <span v-else class="icon-pill" title="Serves every asset of its family, and defines none">Family</span>
                </span>
              </td>
              <td class="module-source"><SourcePill v-if="module.source" :source="module.source" /><template v-else>—</template></td>
              <td>
                <span class="module-states">
                  <span v-for="state in moduleStates(module)" :key="state" class="module-state" :class="state">{{ STATE_LABELS[state] }}</span>
                </span>
              </td>
              <td>{{ module.assetCount }}</td>
              <td class="record-row-actions">
                <button
                  v-if="module.missing && canManage"
                  class="btn-link-inline module-delete"
                  type="button"
                  @click.stop="confirmDelete(module)"
                  @keydown.enter.stop
                >Delete</button>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
      <div v-if="listing.data.value && shown.length === 0" class="table-empty">No module matches these filters.</div>
    </DataTable>

    <RedeployDialog
      v-if="listing.data.value"
      :open="redeploying"
      :status="listing.data.value.deployment"
      @close="redeploying = false; listing.run()"
      @deployed="listing.run()"
    />

    <ConfirmDeleteDialog
      :open="deleted !== null"
      title="Delete a missing module"
      :expected="deleted?.type.name ?? ''"
      :busy="deleting"
      :error="deleteError"
      @close="deleted = null"
      @confirm="remove"
    >
      No instance detects this module any more. Deleting it forgets its state: should its jar come back, it is
      registered again, enabled.
    </ConfirmDeleteDialog>
  </div>
</template>
