<script setup lang="ts">
import {computed, useSlots} from 'vue'

defineProps<{ title: string }>()
const slots = useSlots()
const hasControls = computed(() => Boolean(slots.controls))
</script>

<template>
  <div class="table-card">
    <div class="table-head" :class="{'table-head-controls': hasControls}">
      <template v-if="hasControls">
        <span>{{ title }}</span>
        <div class="table-filters"><slot name="controls" /></div>
      </template>
      <template v-else>{{ title }}</template>
    </div>
    <slot />
  </div>
</template>
