<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const idpSessionEnded = computed(() => route.query.idp === 'true')
</script>

<template>
  <div class="main">
    <div class="notice-card">
      <div class="notice-title">Signed out</div>
      <div class="notice-body">
        <template v-if="idpSessionEnded">You have been signed out of Wombat and of your identity provider.</template>
        <template v-else>Your Wombat session has been cleared. Your identity provider session may still be active on this device.</template>
      </div>
      <a class="btn-apply btn-link" href="/">Back to the report</a>
    </div>
  </div>
</template>
