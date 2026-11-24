<script setup lang="ts">
import { assetTypesByName } from '@/domain/module'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { byDisplayName, displayName, type AssetType, type EnvironmentInfo } from '@/domain/environment'
import { errorText } from '@/services/api/client'
import { fetchEnvironments, fetchLiveEnvironments } from '@/services/api/environments'
import { fetchModules } from '@/services/api/modules'
import { can } from '@/services/auth/permissions'
import { useAppInfo } from '@/services/auth/session'
import { useAsync } from '@/services/tools/useAsync'
import AssetTypeTag from '@/components/AssetTypeTag.vue'
import DataTable from '@/components/DataTable.vue'
import DropdownPicker from '@/components/DropdownPicker.vue'
import LockBadge from '@/components/LockBadge.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import TimestampInfo from '@/components/TimestampInfo.vue'
import PageHead from '@/layout/PageHead.vue'
import DashboardLink from '@/pages/environments/components/DashboardLink.vue'
import { familyCounts, filterEnvironments, STATUS_LABELS, type EnvironmentStatus } from '@/pages/environments/model/environmentList'

interface EnvironmentListing {
  environments: EnvironmentInfo[]
  typesByName: Map<string, AssetType>
  liveIds: Set<string>
}

async function loadListing(): Promise<EnvironmentListing> {
  const [environments, modules, live] = await Promise.all([
    fetchEnvironments(),
    fetchModules(),
    /* Without the dashboard's environments, shortcuts are only left out */
    fetchLiveEnvironments().catch(() => []),
  ])
  return {
    environments: [...environments].sort(byDisplayName),
    typesByName: assetTypesByName(modules),
    liveIds: new Set(live.map(environment => environment.id)),
  }
}

const router = useRouter()
const { appInfo } = useAppInfo()
const listing = useAsync(loadListing)
const search = ref('')
const statuses = ref<EnvironmentStatus[]>([])
const statusOptions = (Object.keys(STATUS_LABELS) as EnvironmentStatus[]).map(value => ({ value, label: STATUS_LABELS[value] }))
const statusSummary = computed(() => statuses.value.length === 0 ? 'Any status' : statuses.value.map(status => STATUS_LABELS[status]).join(', '))
const canCreate = computed(() => can(appInfo.value?.user, 'ui.environments.manage') && can(appInfo.value?.user, 'api.environment.write'))
const shown = computed(() => filterEnvironments(listing.data.value?.environments ?? [], search.value, statuses.value))
const skeletonRows = [0, 1, 2]

function open(environment: EnvironmentInfo): void {
  void router.push({ name: 'environment', params: { id: environment.id } })
}

onMounted(() => listing.run())
</script>

<template>
  <div class="main">
    <div class="list-head">
      <PageHead title="Environments" description="The environments Wombat monitors, and their assets." />
      <RouterLink v-if="canCreate" class="btn-apply btn-link" :to="{ name: 'environment-create' }" id="newEnvironment">New environment</RouterLink>
    </div>

    <div v-if="listing.error.value" class="form-error" role="alert">{{ errorText(listing.error.value) }}</div>

    <DataTable title="Environments">
      <template #controls>
        <input v-model="search" type="search" class="search-input" placeholder="Filter by name" aria-label="Filter environments by name" />
        <DropdownPicker v-model="statuses" label="Status" :options="statusOptions" :summary="statusSummary" picker-id="envStatusPicker" />
      </template>
      <table class="record-table">
        <thead><tr><th>Name</th><th>Status</th><th>Assets</th><th>Updated</th><th></th></tr></thead>
        <tbody>
          <template v-if="listing.loading.value && !listing.data.value">
            <tr v-for="row in skeletonRows" :key="row">
              <td v-for="cell in 5" :key="cell"><SkeletonBlock :width="cell === 1 ? '140px' : '64px'" height="12px" /></td>
            </tr>
          </template>
          <template v-else-if="listing.data.value">
            <tr
              v-for="environment in shown"
              :key="environment.id"
              class="record-row"
              :class="{ inactive: environment.disabledAt }"
              tabindex="0"
              @click="open(environment)"
              @keydown.enter="open(environment)"
            >
              <td class="name">
                <span class="record-row-name">
                  <LockBadge v-if="environment.provisioned" label="" />
                  {{ displayName(environment) }}
                </span>
                <code class="record-id">{{ environment.id }}</code>
              </td>
              <td><span class="status-chip" :class="{ good: !environment.disabledAt }">{{ environment.disabledAt ? 'Disabled' : 'Active' }}</span></td>
              <td>
                <span class="record-row-tags">
                  <AssetTypeTag v-for="entry in familyCounts(environment, listing.data.value.typesByName)" :key="entry.family" :family="entry.family" :count="entry.count" />
                  <span v-if="environment.assets.length === 0" class="record-row-none">None</span>
                </span>
              </td>
              <td><TimestampInfo :updated-at="environment.updatedAt" /></td>
              <td class="record-row-actions"><DashboardLink :environment-id="environment.id" :live="listing.data.value.liveIds.has(environment.id)" compact /></td>
            </tr>
          </template>
        </tbody>
      </table>
      <div v-if="listing.data.value && shown.length === 0" class="table-empty">
        {{ listing.data.value.environments.length === 0 ? 'No environments yet.' : 'No environment matches these filters.' }}
      </div>
    </DataTable>
  </div>
</template>
