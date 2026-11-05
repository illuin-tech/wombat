<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="main">
    <div class="notice-card">
      <div class="notice-title">Not allowed</div>
      <div class="notice-body">
        You are not allowed to open this page. <RouterLink to="/">Back to the report</RouterLink>
      </div>
    </div>
  </div>
</template>
