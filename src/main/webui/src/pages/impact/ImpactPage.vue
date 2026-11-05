<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { findUnrecognizedAssets, type EnvironmentInfo, type LiveEnvironment } from '@/domain/environment'
import type { AssetImpact, TimelineResponse } from '@/domain/impact'
import { asLocalDateTime } from '@/services/format/formatters'
import { ApiError } from '@/services/api/client'
import { NO_DATA_REASON } from '@/services/api/impact'
import { useAppInfo } from '@/services/auth/session'
import { filtersFromRoute } from '@/pages/impact/model/useDashboardQuery'
import { aggregateDashboard, buildDashboardRequests, serviceOptions } from '@/pages/impact/model/dashboard'
import { computeTimeRange } from '@/pages/impact/model/timeRange'
import { useDashboardData } from '@/pages/impact/useDashboardData'
import FilterBar from '@/pages/impact/components/FilterBar.vue'
import TimelineChart from '@/pages/impact/components/TimelineChart.vue'
import KpiRow from '@/pages/impact/components/KpiRow.vue'
import SharesDonut from '@/pages/impact/components/SharesDonut.vue'
import AssetRecap from '@/pages/impact/components/AssetRecap.vue'
import MetricBars from '@/pages/impact/components/MetricBars.vue'
import ServiceTable from '@/pages/impact/components/ServiceTable.vue'

const DEFAULT_MAX_DATE_RANGE_MS = 90 * 86_400_000
const route = useRoute()
const router = useRouter()
const { appInfo } = useAppInfo()
const dashboard = useDashboardData()
const environments = computed(() => dashboard.liveEnvironments.data.value ?? [])
const requestedEnvironmentId = computed(() => String(route.params.environment ?? ''))
const environment = computed(() => environments.value.find(candidate => candidate.id === requestedEnvironmentId.value) ?? environments.value[0] ?? null)
const currentFilters = computed(() => filtersFromRoute(requestedEnvironmentId.value, route.query))
const maxSpanMs = computed(() => appInfo.value?.ui.maxDateRange.millis ?? DEFAULT_MAX_DATE_RANGE_MS)
const range = computed(() => computeTimeRange(currentFilters.value.from, currentFilters.value.to, maxSpanMs.value))
const from = computed(() => asLocalDateTime(range.value.start))
const to = computed(() => asLocalDateTime(range.value.end))
const liveLoading = computed(() => dashboard.liveEnvironments.loading.value || (dashboard.liveEnvironments.data.value === null && dashboard.liveEnvironments.error.value === null))
const impact = computed<AssetImpact[] | null>(() => dashboard.impactEnvironmentId.value === environment.value?.id ? dashboard.impact.data.value : null)
const timeline = computed<TimelineResponse | null>(() => dashboard.timelineEnvironmentId.value === environment.value?.id ? dashboard.timeline.data.value : null)
const impactLoading = computed(() => liveLoading.value || (!!environment.value && impact.value === null && dashboard.impact.error.value === null))
const timelineLoading = computed(() => liveLoading.value || (!!environment.value && timeline.value === null && dashboard.timeline.error.value === null))
const impactRefreshing = computed(() => impact.value !== null && dashboard.impact.loading.value)
const timelineRefreshing = computed(() => timeline.value !== null && dashboard.timeline.loading.value)
const impactError = computed(() => dashboard.impact.error.value)
const noData = computed(() => impactError.value instanceof ApiError && impactError.value.reason === NO_DATA_REASON)
const impactErrorMessage = computed(() => impactError.value instanceof Error ? impactError.value.message : 'Unexpected error, try again.')
const model = computed(() => environment.value && impact.value !== null
  ? aggregateDashboard(impact.value, environment.value.assets, currentFilters.value.services)
  : null)
const allServices = computed(() => serviceOptions(impact.value ?? []))
const persistedEnvironments = computed(() => dashboard.persistedEnvironments.data.value ?? [])
const unrecognizedAssets = computed(() => {
  const persisted = persistedEnvironments.value.find(candidate => candidate.id === environment.value?.id)
  return findUnrecognizedAssets(persisted?.assets ?? [], environment.value?.assets ?? [])
})

watch(() => [
  route.params.environment,
  route.query.from,
  route.query.to,
  route.query.services,
  dashboard.liveEnvironments.data.value,
  appInfo.value?.ui.maxDateRange.millis,
], () => {
  const available = dashboard.liveEnvironments.data.value
  if (!available) return
  const requestedId = requestedEnvironmentId.value
  const selected = available.find(candidate => candidate.id === requestedId) ?? available[0]
  if (!selected) return
  if (selected.id !== requestedId) {
    void router.replace({ name: 'impact', params: { environment: selected.id }, query: route.query })
    return
  }
  const filters = filtersFromRoute(selected.id, route.query)
  const selectedRange = computeTimeRange(filters.from, filters.to, maxSpanMs.value)
  const requests = buildDashboardRequests(selected.id, selected.assets, filters.services, selectedRange.start, selectedRange.end)
  dashboard.loadEvaluation(selected.id, requests)
}, { immediate: true, deep: true })

onMounted(dashboard.loadEnvironments)

function resetFilters(): void {
  void router.push({ name: 'impact', params: { environment: requestedEnvironmentId.value } })
}
</script>

<template>
  <FilterBar
    :environments="environments"
    :environment-id="requestedEnvironmentId"
    :assets="environment?.assets ?? []"
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
        <AssetRecap :model="model" :assets="unrecognizedAssets" :loading="impactLoading" :refreshing="impactRefreshing" />
      </div>
      <MetricBars :model="model" :loading="impactLoading" :refreshing="impactRefreshing" />
      <ServiceTable :model="model" :loading="impactLoading" :refreshing="impactRefreshing" />
    </template>
  </div>
</template>
