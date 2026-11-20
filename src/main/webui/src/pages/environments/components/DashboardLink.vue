<script setup lang="ts">
/** A shortcut to the environment's dashboard, which only shows enabled environments with recognized assets. */
defineProps<{ environmentId: string; live: boolean; compact?: boolean }>()
</script>

<template>
  <RouterLink
    v-if="live"
    class="dashboard-link"
    :class="{ compact }"
    :to="{ name: 'impact', params: { environment: environmentId } }"
    title="Open in the dashboard"
    @click.stop
  >
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 3v18h18" /><path d="M7 15l4-4 3 3 5-6" /></svg>
    <span :class="{ 'visually-hidden': compact }">Dashboard</span>
  </RouterLink>
  <span
    v-else
    class="dashboard-link disabled"
    :class="{ compact }"
    title="Not in the dashboard: the environment is disabled, or has no recognized asset yet"
    aria-disabled="true"
    @click.stop
  >
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 3v18h18" /><path d="M7 15l4-4 3 3 5-6" /></svg>
    <span :class="{ 'visually-hidden': compact }">Dashboard</span>
  </span>
</template>
