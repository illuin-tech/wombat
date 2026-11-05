<script setup lang="ts">
import {create, DateTime, LockPlugin, PresetPlugin, RangePlugin} from '@easepick/bundle'
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import easepickCss from '@easepick/bundle/dist/index.css?url'
import easepickThemeCss from '@/layout/styles/easepick-theme.css?url'

const props = defineProps<{ from: string; to: string; maxSpanMs: number }>()
const emit = defineEmits<{ 'update:from': [value: string]; 'update:to': [value: string] }>()
const spanHint = computed(() => `Maximum range: ${Math.floor(props.maxSpanMs / 86_400_000)} days`)
const pickerInput = ref<HTMLInputElement | null>(null)
const fallback = ref(false)
let picker: InstanceType<typeof create> | null = null
const DAY_MS = 86_400_000

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function utcDay(date: Date): string {
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`
}

function pickerDate(day: string): DateTime {
  const [year, month, date] = day.split('-').map(Number)
  return new DateTime(new Date(year, month - 1, date))
}

function pickerDay(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function endDay(value: string): string {
  if (!value) return ''
  const instant = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value) ? new Date(value) : new Date(`${value}Z`)
  return utcDay(new Date(instant.getTime() - 1))
}

function clampStart(start: string, end: string, maxDays: number): string {
  if (!start || !end || !maxDays) return start
  const startTime = Date.parse(`${start}T00:00:00Z`)
  const endTime = Date.parse(`${end}T00:00:00Z`)
  return Math.floor((endTime - startTime) / DAY_MS) + 1 > maxDays
      ? utcDay(new Date(endTime - (maxDays - 1) * DAY_MS))
      : start
}

function emitRange(start: string, end: string): void {
  if (!start || !end) return
  const maxDays = Math.max(1, Math.floor(props.maxSpanMs / DAY_MS))
  const boundedStart = clampStart(start, end, maxDays)
  emit('update:from', `${boundedStart}T00:00`)
  emit('update:to', `${utcDay(new Date(Date.parse(`${end}T00:00:00Z`) + DAY_MS))}T00:00`)
}

function syncPicker(): void {
  if (!picker || !props.from || !props.to) return
  const start = props.from.slice(0, 10)
  const end = endDay(props.to)
  if (start && end) picker.setDateRange(pickerDate(start), pickerDate(end))
}

function monthStartBack(months: number, end: Date): Date {
  const month = end.getUTCMonth() - months
  const lastDay = new Date(Date.UTC(end.getUTCFullYear(), month + 1, 0)).getUTCDate()
  return new Date(Date.UTC(end.getUTCFullYear(), month, Math.min(end.getUTCDate(), lastDay)))
}

function initialize(): void {
  if (!pickerInput.value) return
  try {
    const today = new Date()
    const todayDay = utcDay(today)
    const exclusiveEnd = new Date(Date.parse(`${todayDay}T00:00:00Z`) + DAY_MS)
    const presets = {
      Today: [pickerDate(todayDay), pickerDate(todayDay)],
      'Last 7 days': [pickerDate(utcDay(new Date(Date.parse(`${todayDay}T00:00:00Z`) - 6 * DAY_MS))), pickerDate(todayDay)],
      'Last 3 months': [pickerDate(utcDay(monthStartBack(3, exclusiveEnd))), pickerDate(todayDay)],
      'Last 6 months': [pickerDate(utcDay(monthStartBack(6, exclusiveEnd))), pickerDate(todayDay)],
      'Last year': [pickerDate(utcDay(monthStartBack(12, exclusiveEnd))), pickerDate(todayDay)],
    }
    const maxDays = Math.max(1, Math.floor(props.maxSpanMs / DAY_MS))
    picker = new create({
      element: pickerInput.value,
      css: [easepickCss, easepickThemeCss],
      zIndex: 20,
      grid: window.matchMedia('(max-width: 720px)').matches ? 1 : 2,
      calendars: window.matchMedia('(max-width: 720px)').matches ? 1 : 2,
      format: 'YYYY-MM-DD',
      plugins: [RangePlugin, PresetPlugin, LockPlugin],
      RangePlugin: {
        tooltip: true,
        startDate: props.from ? pickerDate(props.from.slice(0, 10)) : undefined,
        endDate: props.to ? pickerDate(endDay(props.to)) : undefined,
        repick: true,
        delimiter: ' → ',
      },
      PresetPlugin: {customPreset: presets, position: 'bottom'},
      LockPlugin: {
        filter: (date, picked) => {
          if (!picked || picked.length !== 1) return false
          const anchor = Date.parse(`${pickerDay(picked[0].toJSDate())}T00:00:00Z`)
          const candidateDate = Array.isArray(date) ? date[0] : date
          const candidate = Date.parse(`${pickerDay(candidateDate.toJSDate())}T00:00:00Z`)
          return Math.abs(Math.floor((candidate - anchor) / DAY_MS)) > maxDays - 1
        },
      },
      setup: (instance) => {
        instance.on('select', (event: { detail: { start?: { toJSDate: () => Date }; end?: { toJSDate: () => Date } } }) => {
          const {start, end} = event.detail
          if (start && end) emitRange(pickerDay(start.toJSDate()), pickerDay(end.toJSDate()))
        })
      },
    })
    fallback.value = false
    syncPicker()
  } catch {
    fallback.value = true
  }
}

onMounted(initialize)
watch(() => [props.from, props.to], syncPicker)
onBeforeUnmount(() => picker?.destroy())
</script>

<template>
  <input v-if="!fallback" id="dateRangePicker" ref="pickerInput" class="date-range-picker" :title="spanHint" aria-label="Select date range (UTC)" type="text" placeholder="Pick range…" readonly/>
  <template v-else>
    <input id="dateRangePicker" class="date-range-picker date-range-native" :title="spanHint" aria-label="From date (UTC)" type="date" :value="from.slice(0, 10)"
           @change="emitRange(($event.target as HTMLInputElement).value, endDay(to))"/>
    <span>to</span>
    <input class="date-range-picker date-range-native" aria-label="To date (UTC)" type="date" :value="endDay(to)"
           @change="emitRange(from.slice(0, 10), ($event.target as HTMLInputElement).value)"/>
  </template>
</template>