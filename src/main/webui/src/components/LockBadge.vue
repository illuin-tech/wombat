<script setup lang="ts">
withDefaults(defineProps<{ label?: string; title?: string }>(), {
  label: 'Provisioned',
  title: 'Declared by configuration or by a worker: it cannot be edited here',
})
</script>

<template>
  <span class="lock-badge" :title="title">
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg>
    <span v-if="label">{{ label }}</span>
  </span>
</template>
