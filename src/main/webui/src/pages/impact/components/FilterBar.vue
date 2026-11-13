<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useRouter} from 'vue-router'
import type {ServicesSelection} from '@/pages/impact/model/servicesQuery'
import type {AssetInfo, LiveEnvironment} from '@/domain/environment'
import {encodeFilters, includedServices} from '@/pages/impact/model/dashboard'
import DropdownPicker from '@/components/DropdownPicker.vue'
import DateRangePicker from '@/components/DateRangePicker.vue'

const props = defineProps<{
  environments: LiveEnvironment[]
  environmentId: string
  assets: AssetInfo[]
  services: string[]
  selection: ServicesSelection
  from: string
  to: string
  maxSpanMs: number
  environmentsLoading?: boolean
  assetsLoading?: boolean
  servicesLoading?: boolean
}>()
const emit = defineEmits<{reset: []}>()
const router = useRouter()
const environmentId = ref(props.environmentId)
const from = ref(props.from)
const to = ref(props.to)
const assetIds = ref<string[]>([])
const serviceValues = ref<string[]>([])
const environmentOptions = computed(() => props.environments.map(environment => ({value: environment.id, label: environment.name ?? environment.id})))
const assetOptions = computed(() => props.assets.map(asset => ({
  value: asset.id,
  label: `${asset.name} — ${asset.id}${asset.namespace ? ` / ${asset.namespace}` : ''}`,
  badge: asset.owner ?? undefined,
})))
const environmentSummary = computed(() => props.environmentsLoading ? 'Loading environments…' : props.environments.find(environment => environment.id === environmentId.value)?.name ?? environmentId.value)
const assetSummary = computed(() => props.assetsLoading ? 'Loading assets…' : `${assetIds.value.length} / ${props.assets.length} assets`)
const serviceSummary = computed(() => props.servicesLoading ? 'Loading services…' : `${serviceValues.value.length} / ${props.services.length} services`)

function sync(): void {
  environmentId.value = props.environmentId
  from.value = props.from
  to.value = props.to
  const assetIdsFromRoute = [...props.selection.keys()].filter(id => props.assets.some(asset => asset.id === id))
  assetIds.value = assetIdsFromRoute.length === 0 ? props.assets.map(asset => asset.id) : assetIdsFromRoute
  serviceValues.value = includedServices(props.services, props.selection)
}

watch(() => [props.environmentId, props.from, props.to, props.selection, props.services, props.assets], sync, {deep: true, immediate: true})

function dateQuery(): Record<string, string> {
  const query: Record<string, string> = {}
  if (from.value) query.from = from.value
  if (to.value) query.to = to.value
  return query
}

function selectEnvironment(values: string[]): void {
  const selected = values[0]
  if (!selected || selected === environmentId.value) return
  environmentId.value = selected
  void router.push({name: 'impact', params: {environment: selected}, query: dateQuery()})
}

function selectAssets(values: string[]): void {
  assetIds.value = values
  const query: Record<string, string | string[]> = dateQuery()
  if (values.length > 0) query.services = values
  void router.push({name: 'impact', params: {environment: environmentId.value}, query})
}

function apply(): void {
  const selectedServices = props.services.filter(service => serviceValues.value.includes(service))
  const services = encodeFilters(assetIds.value, selectedServices, props.services)
  const query: Record<string, string | string[]> = dateQuery()
  if (services.length > 0) query.services = services
  void router.push({name: 'impact', params: {environment: environmentId.value}, query})
}
</script>

<template>
  <form id="filterForm" class="filter-bar" method="get" action="" @submit.prevent="apply">
    <div class="filter-group date-range-group">
      <span class="filter-label">Date range</span>
      <DateRangePicker v-model:from="from" v-model:to="to" :max-span-ms="maxSpanMs" />
    </div>
    <div class="filter-group">
      <span class="filter-label">Environment</span>
      <DropdownPicker picker-id="envPicker" input-name="environment" label="Environment" :options="environmentOptions" :model-value="[environmentId]" :summary="environmentSummary" :multiple="false" :disabled="environmentsLoading || environments.length === 0" @update:model-value="selectEnvironment" />
    </div>
    <div class="filter-group">
      <span class="filter-label">Assets</span>
      <DropdownPicker picker-id="assetPicker" input-name="assets" label="Assets" :options="assetOptions" :model-value="assetIds" :summary="assetSummary" :disabled="assetsLoading || assets.length === 0" @update:model-value="selectAssets" />
    </div>
    <div class="filter-group">
      <span class="filter-label">Services</span>
      <DropdownPicker picker-id="servicePicker" input-name="containers" label="Services" :options="services.map(service => ({value: service, label: service}))" :model-value="serviceValues" :summary="serviceSummary" :disabled="servicesLoading || services.length === 0" searchable actions @update:model-value="serviceValues = $event" />
    </div>
    <button class="btn-apply" type="submit">Apply</button>
    <button id="btnReset" class="btn-reset" type="button" @click="emit('reset')">Reset</button>
  </form>
</template>
