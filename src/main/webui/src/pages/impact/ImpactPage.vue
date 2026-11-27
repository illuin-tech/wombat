<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { findUnrecognizedAssets, withEnvironmentNames, withRecordDetails } from '@/domain/environment'
import { evaluationFailures, type ImpactResult, type TimelineResponse } from '@/domain/impact'
import { asLocalDateTime } from '@/services/format/formatters'
import { ApiError } from '@/services/api/client'
import { NO_DATA_REASON } from '@/services/api/impact'
import { useAppInfo } from '@/services/auth/session'
import { datesQuery, filtersFor, rememberDashboard, resetServices, servicesState } from '@/pages/impact/model/useDashboardQuery'
import { aggregateDashboard, buildDashboardRequests, serviceOptions } from '@/pages/impact/model/dashboard'
import { computeTimeRange } from '@/pages/impact/model/timeRange'
import { failedAssets } from '@/pages/impact/model/failures'
import { useDashboardData } from '@/pages/impact/useDashboardData'
import FilterBar from '@/pages/impact/components/FilterBar.vue'
import TimelineChart from '@/pages/impact/components/TimelineChart.vue'
import KpiRow from '@/pages/impact/components/KpiRow.vue'
import SharesDonut from '@/pages/impact/components/SharesDonut.vue'
import AssetRecap from '@/pages/impact/components/AssetRecap.vue'
import MetricBars from '@/pages/impact/components/MetricBars.vue'
import ServiceTable from '@/pages/impact/components/ServiceTable.vue'
import EvaluationFailureList from '@/pages/impact/components/EvaluationFailureList.vue'

const DEFAULT_MAX_DATE_RANGE_MS = 90 * 86_400_000
const route = useRoute()
const router = useRouter()
const { appInfo } = useAppInfo()
const dashboard = useDashboardData()
const environments = computed(() => withEnvironmentNames(dashboard.liveEnvironments.data.value ?? [], dashboard.persistedEnvironments.data.value ?? []))
const requestedEnvironmentId = computed(() => String(route.params.environment ?? ''))
const environment = computed(() => environments.value.find(candidate => candidate.id === requestedEnvironmentId.value) ?? environments.value[0] ?? null)
const currentFilters = computed(() => filtersFor(requestedEnvironmentId.value, route.query))
const maxSpanMs = computed(() => appInfo.value?.ui.maxDateRange.millis ?? DEFAULT_MAX_DATE_RANGE_MS)
const range = computed(() => computeTimeRange(currentFilters.value.from, currentFilters.value.to, maxSpanMs.value))
const from = computed(() => asLocalDateTime(range.value.start))
const to = computed(() => asLocalDateTime(range.value.end))
const liveLoading = computed(() => dashboard.liveEnvironments.loading.value || (dashboard.liveEnvironments.data.value === null && dashboard.liveEnvironments.error.value === null))
const impactResult = computed<ImpactResult | null>(() => dashboard.impactEnvironmentId.value === environment.value?.id ? dashboard.impact.data.value : null)
const impact = computed(() => impactResult.value?.impacts ?? null)
const timeline = computed<TimelineResponse | null>(() => dashboard.timelineEnvironmentId.value === environment.value?.id ? dashboard.timeline.data.value : null)
const impactLoading = computed(() => liveLoading.value || (!!environment.value && impact.value === null && dashboard.impact.error.value === null))
const timelineLoading = computed(() => liveLoading.value || (!!environment.value && timeline.value === null && dashboard.timeline.error.value === null))
const impactRefreshing = computed(() => impact.value !== null && dashboard.impact.loading.value)
const timelineRefreshing = computed(() => timeline.value !== null && dashboard.timeline.loading.value)
const impactError = computed(() => dashboard.impact.error.value)
const noData = computed(() => impactError.value instanceof ApiError && impactError.value.reason === NO_DATA_REASON)
const impactErrorMessage = computed(() => impactError.value instanceof Error ? impactError.value.message : 'Unexpected error, try again.')
const model = computed(() => environment.value && impact.value !== null
  ? aggregateDashboard(impact.value, liveAssets.value, currentFilters.value.services)
  : null)
const allServices = computed(() => serviceOptions(impact.value ?? []))
const persistedEnvironments = computed(() => dashboard.persistedEnvironments.data.value ?? [])
const persistedEnvironment = computed(() => persistedEnvironments.value.find(candidate => candidate.id === environment.value?.id))
const unrecognizedAssets = computed(() => findUnrecognizedAssets(persistedEnvironment.value?.assets ?? [], environment.value?.assets ?? []))
const liveAssets = computed(() => withRecordDetails(environment.value?.assets ?? [], persistedEnvironment.value?.assets ?? []))
/* Told by the impact, or by its no-data error when no asset could be evaluated */
const failures = computed(() => failedAssets(
  impactResult.value?.failures ?? (noData.value && impactError.value instanceof ApiError ? evaluationFailures(impactError.value.metadata) : []),
  [...liveAssets.value, ...(persistedEnvironment.value?.assets ?? [])],
  environments.value,
))

watch(() => [
  route.params.environment,
  route.query.from,
  route.query.to,
  servicesState,
  dashboard.liveEnvironments.data.value,
  appInfo.value?.ui.maxDateRange.millis,
], () => {
  const available = dashboard.liveEnvironments.data.value
  if (!available) return
  const requestedId = requestedEnvironmentId.value
  const selected = available.find(candidate => candidate.id === requestedId) ?? available[0]
  if (!selected) return
  const filters = filtersFor(selected.id, route.query)
  const selectedRange = computeTimeRange(filters.from, filters.to, maxSpanMs.value)
  // Always show the effective range in the URL, so the default one can be shared as is.
  if (selected.id !== requestedId || !filters.from || !filters.to) {
    const query = { ...route.query, ...datesQuery(asLocalDateTime(selectedRange.start), asLocalDateTime(selectedRange.end)) }
    void router.replace({ name: 'impact', params: { environment: selected.id }, query })
    return
  }
  rememberDashboard(selected.id, filters.from, filters.to)
  const requests = buildDashboardRequests(selected.id, selected.assets, filters.services, selectedRange.start, selectedRange.end)
  dashboard.loadEvaluation(selected.id, requests)
}, { immediate: true, deep: true })

onMounted(dashboard.loadEnvironments)

function resetFilters(): void {
  resetServices()
  void router.push({ name: 'impact', params: { environment: requestedEnvironmentId.value } })
}
</script>

<template>
  <FilterBar
    :environments="environments"
    :environment-id="requestedEnvironmentId"
    :assets="liveAssets"
    :services="allServices"
    :selection="currentFilters.services"
    :from="from"
    :to="to"
    :max-span-ms="maxSpanMs"
    :environments-loading="liveLoading"
    :assets-loading="liveLoading || !environment"
    :services-loading="impactLoading"
    @reset="resetFilters"
  />
  <div class="main">
    <TimelineChart
      v-if="timelineLoading || (timeline && timeline.bucketStarts.length > 0)"
      :timeline="timeline"
      :loading="timelineLoading"
      :refreshing="timelineRefreshing"
    />
    <div v-if="noData" class="error-card">
      <div class="error-header"><div class="error-dot"/><span class="error-label">No data</span></div>
      <div class="error-title">No activity found</div>
      <div class="error-body">No metrics were recorded for this environment in the selected time range. Try a wider range or check that Wombat was running and monitoring it.</div>
      <div v-if="failures.length" class="error-failures">
        <div class="error-failures-title">{{ failures.length === 1 ? 'An asset' : `${failures.length} assets` }} could not be evaluated</div>
        <EvaluationFailureList :failures="failures" />
      </div>
    </div>
    <div v-else-if="impactError" class="error-card">
      <div class="error-header"><div class="error-dot"/><span class="error-label">Error</span></div>
      <div class="error-title">The impact could not be loaded</div>
      <div class="error-body">{{ impactErrorMessage }}</div>
    </div>
    <template v-else>
      <KpiRow :model="model" :loading="impactLoading" :refreshing="impactRefreshing" />
      <div class="overview-row">
        <SharesDonut :model="model" :loading="impactLoading" :refreshing="impactRefreshing" />
        <AssetRecap :model="model" :assets="unrecognizedAssets" :failures="failures" :loading="impactLoading" :refreshing="impactRefreshing" />
      </div>
      <MetricBars :model="model" :loading="impactLoading" :refreshing="impactRefreshing" />
      <ServiceTable :model="model" :loading="impactLoading" :refreshing="impactRefreshing" />
    </template>
  </div>
</template>
