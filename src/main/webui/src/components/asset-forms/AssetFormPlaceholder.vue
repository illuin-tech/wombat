<script setup lang="ts">
import './placeholder.css'

/* Shaped like a form of a few fields and a group, until the schema and the form generator are loaded */
const fields = ['42%', '68%', '55%']
const groupFields = ['60%', '48%']
</script>

<template>
  <div class="asset-form-placeholder blur-pending" role="status" aria-busy="true" aria-label="Loading the form">
    <div v-for="(width, index) in fields" :key="`field-${index}`" class="asset-form-placeholder-field">
      <span class="asset-form-placeholder-label" :style="{ width: `calc(${width} / 3)` }" />
      <span class="asset-form-placeholder-input" :style="{ width }" />
    </div>
    <div class="asset-form-placeholder-group">
      <span class="asset-form-placeholder-label" style="width: 18%" />
      <div v-for="(width, index) in groupFields" :key="`group-${index}`" class="asset-form-placeholder-field">
        <span class="asset-form-placeholder-label" :style="{ width: `calc(${width} / 3)` }" />
        <span class="asset-form-placeholder-input" :style="{ width }" />
      </div>
    </div>
  </div>
</template>
