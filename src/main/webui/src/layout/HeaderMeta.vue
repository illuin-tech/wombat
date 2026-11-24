<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import {useAppInfo} from '@/services/auth/session'
import {computeTimeRange, formatRangeLength} from '@/pages/impact/model/timeRange'
import {filtersFor} from '@/pages/impact/model/useDashboardQuery'
import {asIsoUtc, formatUtcDayRange} from '@/services/format/formatters'

const route = useRoute()
const {appInfo} = useAppInfo()

const range = computed(() => {
  if (!appInfo.value || route.name !== 'impact') return null
  const {from, to} = filtersFor(String(route.params.environment ?? ''), route.query)
  return computeTimeRange(from, to, appInfo.value.ui.maxDateRange.millis)
})
const start = computed(() => range.value ? asIsoUtc(range.value.start) : '')
const end = computed(() => range.value ? asIsoUtc(range.value.end) : '')
const length = computed(() => range.value ? formatRangeLength(range.value) : '')
</script>

<template>
  <div v-if="range" id="headerMeta" class="header-meta" :data-start="start" :data-end="end">
    {{ formatUtcDayRange(start, end) }}<span class="header-meta-length">{{ length }}</span>
  </div>
</template>
