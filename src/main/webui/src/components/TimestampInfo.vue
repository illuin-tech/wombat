<script setup lang="ts">
import {computed} from 'vue'
import {useTimeAgo} from '@vueuse/core'
import {asUserDate} from '@/services/format/formatters'

const props = defineProps<{ createdAt?: string | null; updatedAt?: string | null }>()
const updated = useTimeAgo(() => props.updatedAt ?? Date.now())
const created = computed(() => asUserDate(props.createdAt))
</script>

<template>
  <span class="timestamp-info">
    <span v-if="updatedAt" class="timestamp-updated" :title="asUserDate(updatedAt)">Updated {{ updated }}</span>
    <span v-if="updatedAt && createdAt" aria-hidden="true"> · </span>
    <span v-if="createdAt" class="timestamp-created">Created {{ created }}</span>
  </span>
</template>
