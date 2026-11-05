<script setup lang="ts">
import AppHeader from '@/layout/AppHeader.vue'
</script>

<template>
  <AppHeader>
    <template #headerExtra>
      <slot name="headerExtra" />
    </template>
    <template #userMenu>
      <slot name="userMenu" />
    </template>
  </AppHeader>
  <slot />
</template>
