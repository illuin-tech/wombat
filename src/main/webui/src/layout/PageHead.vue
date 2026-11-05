<script setup lang="ts">
import { RouterLink } from 'vue-router'

defineProps<{title: string; description?: string}>()
</script>

<template>
  <div class="page-head">
    <RouterLink class="page-back" to="/">&larr; Back to the report</RouterLink>
    <h2 class="page-title">{{ title }}</h2>
    <p class="page-desc"><slot>{{ description }}</slot></p>
  </div>
</template>
