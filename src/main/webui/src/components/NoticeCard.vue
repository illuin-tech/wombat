<script setup lang="ts">
defineProps<{ title: string; tone?: 'info' | 'warning' | 'error' }>()
</script>

<template>
  <div class="notice-card">
    <div class="notice-title">{{ title }}</div>
    <div class="notice-body"><slot/></div>
  </div>
</template>