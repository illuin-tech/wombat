<script setup>
import { ref } from 'vue';
import DateRange from './DateRange.vue';
import Picker from './Picker.vue';
import { dateParams, serviceParams } from '../utils/filter-query.js';

const props = defineProps({
  dates: { type: Object, required: true },
  environments: { type: Array, default: () => [] },
  assets: { type: Array, default: () => [] },
  services: { type: Array, default: () => [] },
  selectedEnvironments: { type: Array, default: () => [] },
  selectedAssets: { type: Array, default: () => [] },
  selectedServices: { type: Array, default: () => [] },
  report: Boolean,
  navigate: { type: Function, default: url => { window.location.href = url; } }
});
const form = ref(null);
const environments = ref([...props.selectedEnvironments]);
const assets = ref([...props.selectedAssets]);
const services = ref([...props.selectedServices]);
function base() {
  const params = dateParams(form.value);
  if (props.report && environments.value[0]) params.set('environment', environments.value[0]);
  return params;
}
function submit() {
  const params = props.report ? serviceParams(base(), assets.value, services.value, props.services.length) : base();
  props.navigate(`?${params}`);
}
function environmentChanged(values) {
  environments.value = values;
  props.navigate(`?${base()}`);
}
function assetsChanged(values) {
  assets.value = values;
  props.navigate(`?${serviceParams(base(), values, [], 0)}`);
}
function reset() { props.navigate(window.location.pathname); }
</script>

<template>
  <form id="filterForm" ref="form" class="filter-bar" method="get" @submit.prevent="submit">
    <DateRange v-bind="dates"/>
    <template v-if="report">
      <div class="filter-group">
        <span class="filter-label">Environment</span>
        <Picker v-model="environments" prefix="env" kind="environments" :options="props.environments" radio @change="environmentChanged"/>
      </div>
      <div class="filter-group">
        <span class="filter-label">Assets</span>
        <Picker v-model="assets" prefix="asset" kind="assets" :options="props.assets" @change="assetsChanged"/>
      </div>
      <div class="filter-group">
        <span class="filter-label">Services</span>
        <Picker v-model="services" prefix="service" kind="services" :options="props.services" searchable/>
      </div>
    </template>
    <button type="submit" class="btn-apply">Apply</button>
    <button v-if="report" id="btnReset" type="button" class="btn-reset" @click="reset">Reset</button>
  </form>
</template>
