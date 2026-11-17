<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import {useAppInfo} from '@/services/auth/session'
import {computeTimeRange} from '@/pages/impact/model/timeRange'
import {dashboardSelection} from '@/pages/impact/model/useDashboardQuery'
import {asIsoUtc, formatUtcDayRange} from '@/services/format/formatters'

const route = useRoute()
const {appInfo} = useAppInfo()

const range = computed(() => {
  if (!appInfo.value || route.name !== 'impact') return null
  return computeTimeRange(dashboardSelection.value.from, dashboardSelection.value.to, appInfo.value.ui.maxDateRange.millis)
})
const start = computed(() => range.value ? asIsoUtc(range.value.start) : '')
const end = computed(() => range.value ? asIsoUtc(range.value.end) : '')
</script>

<template>
  <div v-if="range" id="headerMeta" class="header-meta" :data-start="start" :data-end="end">{{ formatUtcDayRange(start, end) }}</div>
</template>
