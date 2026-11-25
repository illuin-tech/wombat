<script setup lang="ts">
import { inject } from 'vue'
import type { ModuleSource } from '@/domain/module'
import { IN_PILL_HINT } from '@/components/PillHint.vue'

defineProps<{ source: ModuleSource }>()
const inHint = inject(IN_PILL_HINT, false)

const LABELS: Record<ModuleSource, string> = {
  core: 'Core',
  extension: 'Extension',
}

const TITLES: Record<ModuleSource, string> = {
  core: 'Shipped with Wombat',
  extension: 'Loaded from a jar of the extension folder',
}
</script>

<template>
  <span class="icon-pill" :class="source" :title="inHint ? undefined : TITLES[source]">
    <svg v-if="source === 'core'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M11 21.7a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.7l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.7z" />
      <path d="M12 22V12" />
      <path d="m3.3 7 7.7 4.7a2 2 0 0 0 2 0L20.7 7" />
      <path d="m7.5 4.3 9 5.1" />
    </svg>
    <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M15.4 4.4a1 1 0 0 0 1.7-.5 2.5 2.5 0 1 1 3 3 1 1 0 0 0-.5 1.7l1.7 1.7a2.4 2.4 0 0 1 0 3.4l-1.7 1.7a1 1 0 0 1-1.7-.5 2.5 2.5 0 1 0-3 3 1 1 0 0 1 .5 1.7l-1.7 1.7a2.4 2.4 0 0 1-3.4 0l-1.7-1.7a1 1 0 0 0-1.7.5 2.5 2.5 0 1 1-3-3 1 1 0 0 0 .5-1.7l-1.7-1.7a2.4 2.4 0 0 1 0-3.4l1.7-1.7a1 1 0 0 1 1.7.5 2.5 2.5 0 1 0 3-3 1 1 0 0 1-.5-1.7l1.7-1.7a2.4 2.4 0 0 1 3.4 0z" />
    </svg>
    {{ LABELS[source] }}
  </span>
</template>
