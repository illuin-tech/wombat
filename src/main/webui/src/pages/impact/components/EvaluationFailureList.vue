<script setup lang="ts">
import type {FailedAsset} from '@/pages/impact/model/failures'

defineProps<{ failures: FailedAsset[] }>()
</script>

<template>
  <div class="unrecognized-assets-list">
    <div v-for="failure in failures" :key="`${failure.environment}-${failure.id}`" class="unrecognized-asset-item">
      <div class="unrecognized-asset-name">{{ failure.name }}</div>
      <div class="unrecognized-asset-meta"><span class="unrecognized-meta-label">ID:</span> <code class="unrecognized-code">{{ failure.id }}</code> <span
          class="unrecognized-meta-label">Environment:</span> <code class="unrecognized-code">{{ failure.environment }}</code></div>
      <div class="failure-reason">{{ failure.reason }}</div>
    </div>
  </div>
</template>
