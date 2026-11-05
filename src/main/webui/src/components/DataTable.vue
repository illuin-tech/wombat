<script setup lang="ts">
import {computed, useSlots} from 'vue'

defineProps<{ title: string }>()
const slots = useSlots()
const hasControls = computed(() => Boolean(slots.controls))
</script>

<template>
  <div class="table-card">
    <div class="table-head" :class="{'apikey-table-head': hasControls}">
      <template v-if="hasControls">
        <span>{{ title }}</span>
        <div class="apikey-filters"><slot name="controls" /></div>
      </template>
      <template v-else>{{ title }}</template>
    </div>
    <slot />
  </div>
</template>
