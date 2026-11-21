<script setup lang="ts">
import { computed, ref } from 'vue'
import type { AssetInfo, AssetType } from '@/domain/environment'
import { PERIOD_LABELS, PERIODS, type SimulationEvaluation, type SimulationPeriod } from '@/domain/simulation'
import { asDecimal } from '@/services/format/formatters'
import DataTable from '@/components/DataTable.vue'
import NoticeCard from '@/components/NoticeCard.vue'
import ToggleSwitch from '@/components/ToggleSwitch.vue'
import KpiRow from '@/pages/impact/components/KpiRow.vue'
import MetricBars from '@/pages/impact/components/MetricBars.vue'
import ServiceTable from '@/pages/impact/components/ServiceTable.vue'
import SharesDonut from '@/pages/impact/components/SharesDonut.vue'
import EvaluationSettingsDialog from '@/pages/simulations/components/EvaluationSettingsDialog.vue'
import { costText, dashboardModel, resultRows, STATUS_LABELS, STATUS_TONES, totalRow, windowText } from '@/pages/simulations/model/results'

const props = defineProps<{
  evaluation: SimulationEvaluation | null
  error: string | null
  computing: boolean
  assets: readonly AssetInfo[]
  typesByName: ReadonlyMap<string, AssetType>
  canEdit: boolean
}>()

const period = defineModel<SimulationPeriod>('period', { required: true })
const referenceWindow = defineModel<SimulationPeriod>('referenceWindow', { required: true })
const impact = defineModel<boolean>('impact', { required: true })
const cost = defineModel<boolean>('cost', { required: true })

type Breakdown = 'assets' | 'services'
const BREAKDOWNS: { value: Breakdown; label: string }[] = [{ value: 'assets', label: 'By asset' }, { value: 'services', label: 'By service' }]

const model = computed(() => props.evaluation && dashboardModel(props.evaluation, period.value, props.assets))
const rows = computed(() => props.evaluation ? resultRows(props.evaluation, period.value, props.assets, props.typesByName) : [])
const total = computed(() => props.evaluation && totalRow(props.evaluation, period.value))
const unevaluated = computed(() => props.evaluation?.assets.filter(result => result.status !== 'ok').length ?? 0)
const recomputing = computed(() => props.computing && !!props.evaluation)
const breakdown = ref<Breakdown>('assets')
const settingsOpen = ref(false)
</script>

<template>
  <section class="sim-results">
    <div class="chart-head">
      <div class="chart-title">Results</div>
      <div class="chart-controls">
        <div class="chart-pills" id="resultPeriods" role="group" aria-label="Period shown">
          <button
            v-for="candidate in PERIODS"
            :key="candidate"
            type="button"
            class="chart-pill"
            :class="{ active: period === candidate }"
            :aria-pressed="period === candidate"
            @click="period = candidate"
          >Per {{ PERIOD_LABELS[candidate].toLowerCase() }}</button>
        </div>
        <button class="icon-button" type="button" id="evaluationSettings" aria-label="Evaluation settings" title="Evaluation settings" @click="settingsOpen = true">
          <span class="icon icon-settings" aria-hidden="true" />
        </button>
        <ToggleSwitch v-model="impact" label="Impact" />
        <ToggleSwitch v-model="cost" label="Cost" />
      </div>
    </div>

    <NoticeCard v-if="error" title="The simulation could not be evaluated" tone="error">{{ error }}</NoticeCard>
    <p v-if="!impact && !cost" class="table-empty">Turn Impact or Cost on to evaluate the simulation.</p>

    <div v-else class="sim-results-body" :aria-busy="computing">
      <p class="chart-note sim-results-window">
        <template v-if="evaluation">{{ windowText(evaluation, period) }}</template>
        <template v-else-if="computing">Evaluating…</template>
      </p>
      <KpiRow v-if="impact" :model="model" :loading="!evaluation" :refreshing="recomputing" />

      <div class="chart-pills" id="resultBreakdowns" role="group" aria-label="Breakdown">
        <button
          v-for="candidate in BREAKDOWNS"
          :key="candidate.value"
          type="button"
          class="chart-pill"
          :class="{ active: breakdown === candidate.value }"
          :aria-pressed="breakdown === candidate.value"
          @click="breakdown = candidate.value"
        >{{ candidate.label }}</button>
      </div>

      <template v-if="breakdown === 'services'">
        <p v-if="!impact" class="table-empty">Turn Impact on to see the services.</p>
        <template v-else>
          <p v-if="unevaluated > 0" class="chart-note sim-unevaluated">
            {{ unevaluated === 1 ? 'One asset has' : `${unevaluated} assets have` }} no results: see By asset.
          </p>
          <div class="sim-charts-row">
            <SharesDonut :model="model" :loading="!evaluation" :refreshing="recomputing" />
            <MetricBars :model="model" :loading="!evaluation" :refreshing="recomputing" />
          </div>
          <ServiceTable :model="model" :loading="!evaluation" :refreshing="recomputing" />
        </template>
      </template>

      <DataTable v-else title="Per asset" :class="{ 'is-refreshing': recomputing }">
        <table class="sim-results-table">
          <thead>
            <tr>
              <th>Asset</th>
              <th>Status</th>
              <template v-if="impact">
                <th>GWP (kgCO2eq)</th>
                <th>Primary energy (MJ)</th>
                <th>Abiotic resources (kgSbeq)</th>
              </template>
              <th v-if="cost">Cost</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id" :class="{ inactive: row.status !== 'ok' }">
              <td class="name">
                {{ row.name }}
                <span v-if="row.coverageNote" class="sim-coverage form-hint">{{ row.coverageNote }}</span>
              </td>
              <td>
                <span class="status-chip" :class="STATUS_TONES[row.status]" :title="row.message ?? undefined">{{ STATUS_LABELS[row.status] }}</span>
                <span v-if="row.message" class="sim-status-message">{{ row.message }}</span>
              </td>
              <template v-if="impact">
                <td class="metric gwp bold">{{ asDecimal(row.gwp) }}</td>
                <td class="metric pe bold">{{ asDecimal(row.pe) }}</td>
                <td class="metric adp bold">{{ asDecimal(row.adp) }}</td>
              </template>
              <td v-if="cost">
                <template v-if="row.cost?.length">{{ costText(row.cost) }}</template>
                <span v-else-if="row.status === 'ok'" class="sim-not-computed">Not computed</span>
                <template v-else>—</template>
              </td>
            </tr>
          </tbody>
          <tfoot v-if="total && rows.length > 0">
            <tr class="sim-total">
              <td class="name">Total</td>
              <td />
              <template v-if="impact">
                <td class="metric gwp bold">{{ asDecimal(total.gwp) }}</td>
                <td class="metric pe bold">{{ asDecimal(total.pe) }}</td>
                <td class="metric adp bold">{{ asDecimal(total.adp) }}</td>
              </template>
              <td v-if="cost">
                <template v-if="total.cost?.length">{{ costText(total.cost) }}</template>
                <span v-else class="sim-not-computed">Not computed</span>
              </td>
            </tr>
          </tfoot>
        </table>
        <div v-if="evaluation && rows.length === 0" class="table-empty">Add assets to see what the simulation would emit.</div>
      </DataTable>

      <div v-if="recomputing" class="sim-recomputing" role="status">Recomputing…</div>
    </div>

    <EvaluationSettingsDialog v-model:reference-window="referenceWindow" :open="settingsOpen" :can-edit="canEdit" @close="settingsOpen = false" />
  </section>
</template>
