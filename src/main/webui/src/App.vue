<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppLayout from '@/layout/AppLayout.vue'
import HeaderMeta from '@/layout/HeaderMeta.vue'
import UserMenu from '@/layout/UserMenu.vue'
</script>

<template>
  <AppLayout>
    <template #headerExtra>
      <HeaderMeta />
    </template>
    <template #userMenu>
      <UserMenu />
    </template>
    <RouterView />
  </AppLayout>
</template>
