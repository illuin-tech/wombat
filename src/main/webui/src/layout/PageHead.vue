<script setup lang="ts">
defineProps<{title: string; description?: string}>()
</script>

<template>
  <div class="page-head">
    <h2 class="page-title">{{ title }}</h2>
    <p class="page-desc"><slot>{{ description }}</slot></p>
  </div>
</template>
