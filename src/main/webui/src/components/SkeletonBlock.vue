<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ width?: string; height?: string }>(), {
  width: '100%',
  height: '1em',
})
const style = computed(() => ({ width: props.width, height: props.height }))
</script>

<template>
  <span class="skeleton-block" :style="style" aria-hidden="true" />
</template>
