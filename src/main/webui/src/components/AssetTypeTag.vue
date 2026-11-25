<script setup lang="ts">
import {computed, inject} from 'vue'
import {FAMILIES} from '@/domain/assetTypeDisplay'
import type {ServiceFamily} from '@/domain/environment'
import {IN_PILL_HINT} from '@/components/PillHint.vue'

const props = defineProps<{ family: ServiceFamily; count?: number }>()
const inHint = inject(IN_PILL_HINT, false)

const tag = computed(() => FAMILIES[props.family])
</script>

<template>
  <span class="asset-type-tag" :class="tag.css" :title="inHint || count === undefined ? undefined : `${count} ${tag.label} asset${count === 1 ? '' : 's'}`">
    <img v-if="tag.icon" class="svc-icon" :src="tag.icon" :alt="tag.label" />{{ count === undefined ? tag.label.toUpperCase() : count }}
  </span>
</template>
