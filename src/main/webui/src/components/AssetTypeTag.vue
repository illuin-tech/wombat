<script setup lang="ts">
import {computed} from 'vue'
import {FAMILIES} from '@/domain/assetTypeDisplay'
import type {ServiceFamily} from '@/domain/environment'

const props = defineProps<{ family: ServiceFamily; count?: number }>()

const tag = computed(() => FAMILIES[props.family])
</script>

<template>
  <span class="asset-type-tag" :class="tag.css" :title="count === undefined ? undefined : `${count} ${tag.label} asset${count === 1 ? '' : 's'}`">
    <img v-if="tag.icon" class="svc-icon" :src="tag.icon" :alt="tag.label" />{{ count === undefined ? tag.label.toUpperCase() : count }}
  </span>
</template>
