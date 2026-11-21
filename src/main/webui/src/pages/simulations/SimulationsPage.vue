<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { parentEnvironmentRemoved, type SimulationInfo, type SimulationSummary } from '@/domain/simulation'
import { errorText } from '@/services/api/client'
import { fetchSimulations } from '@/services/api/simulations'
import { can } from '@/services/auth/permissions'
import { useAppInfo } from '@/services/auth/session'
import { useAsync } from '@/services/tools/useAsync'
import DataTable from '@/components/DataTable.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'
import TimestampInfo from '@/components/TimestampInfo.vue'
import PageHead from '@/layout/PageHead.vue'
import DuplicateSimulationDialog from '@/pages/simulations/components/DuplicateSimulationDialog.vue'
import { filterSimulations } from '@/pages/simulations/model/simulationList'

const router = useRouter()
const { appInfo } = useAppInfo()
const listing = useAsync(fetchSimulations)
const search = ref('')
const duplicated = ref<SimulationSummary | null>(null)
const canManage = computed(() => can(appInfo.value?.user, 'ui.simulations.manage') && can(appInfo.value?.user, 'api.simulation.write'))
const shown = computed(() => filterSimulations(listing.data.value ?? [], search.value))
const skeletonRows = [0, 1, 2]

function open(simulation: { id: string }): void {
  void router.push({ name: 'simulation', params: { id: simulation.id } })
}

function onDuplicated(simulation: SimulationInfo): void {
  duplicated.value = null
  open(simulation)
}

onMounted(() => listing.run())
</script>

<template>
  <div class="main">
    <div class="env-list-head">
      <PageHead title="Simulations" description="A sandbox for estimating system impacts, you can work from copies of real assets or definitions written from scratch." />
      <RouterLink v-if="canManage" class="btn-apply btn-link" :to="{ name: 'simulation-create' }" id="newSimulation">New simulation</RouterLink>
    </div>

    <div v-if="listing.error.value" class="form-error" role="alert">{{ errorText(listing.error.value) }}</div>

    <DataTable title="Simulations">
      <template #controls>
        <input v-model="search" type="search" class="search-input" placeholder="Filter by name" aria-label="Filter simulations by name" />
      </template>
      <table class="env-table">
        <thead><tr><th>Name</th><th>Source environment</th><th>Assets</th><th>Updated</th><th></th></tr></thead>
        <tbody>
          <template v-if="listing.loading.value && !listing.data.value">
            <tr v-for="row in skeletonRows" :key="row">
              <td v-for="cell in 5" :key="cell"><SkeletonBlock :width="cell === 1 ? '140px' : '64px'" height="12px" /></td>
            </tr>
          </template>
          <template v-else-if="listing.data.value">
            <tr
              v-for="simulation in shown"
              :key="simulation.id"
              class="env-row"
              tabindex="0"
              @click="open(simulation)"
              @keydown.enter="open(simulation)"
            >
              <td class="name">
                <span class="env-row-name">{{ simulation.name }}</span>
                <code class="env-row-id">{{ simulation.id }}</code>
              </td>
              <td>
                <span v-if="parentEnvironmentRemoved(simulation)" class="env-row-none">Source environment removed</span>
                <span v-else-if="simulation.parentEnvironmentName">{{ simulation.parentEnvironmentName }}</span>
                <span v-else class="env-row-none">None</span>
              </td>
              <td>{{ simulation.assetCount }}</td>
              <td><TimestampInfo :updated-at="simulation.updatedAt" /></td>
              <td class="env-row-actions">
                <button v-if="canManage" class="btn-link-inline sim-duplicate-action" type="button" @click.stop="duplicated = simulation">Duplicate</button>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
      <div v-if="listing.data.value && shown.length === 0" class="table-empty">
        {{ listing.data.value.length === 0 ? 'No simulations yet.' : 'No simulation matches this filter.' }}
      </div>
    </DataTable>

    <DuplicateSimulationDialog :source="duplicated" @close="duplicated = null" @duplicated="onDuplicated" />
  </div>
</template>
