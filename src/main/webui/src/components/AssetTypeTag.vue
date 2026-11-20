<script setup lang="ts">
import {computed} from 'vue'
import type {ServiceFamily} from '@/domain/environment'

const props = defineProps<{ family: ServiceFamily; count?: number }>()

const tag = computed(() => {
  if (props.family === 'KUBERNETES_CONTAINER') return { css: 'kubernetes', icon: '/icons/docker.svg', label: 'KUBERNETES' }
  if (props.family === 'LLM') return { css: 'llm', icon: '/icons/llm.svg', label: 'LLM' }
  return { css: 'measure', icon: null, label: 'UNKNOWN' }
})
</script>

<template>
  <span class="asset-type-tag" :class="tag.css" :title="count === undefined ? undefined : `${count} ${tag.label.toLowerCase()} asset${count === 1 ? '' : 's'}`">
    <img v-if="tag.icon" class="svc-icon" :src="tag.icon" :alt="tag.label.toLowerCase()" />{{ count === undefined ? tag.label : count }}
  </span>
</template>
