<script setup lang="ts">
defineProps<{ label: string; disabled?: boolean }>()
const checked = defineModel<boolean>({ required: true })
</script>

<template>
  <button
    class="toggle-switch"
    :class="{ on: checked }"
    type="button"
    role="switch"
    :aria-checked="checked"
    :aria-label="label"
    :disabled="disabled"
    @click="checked = !checked"
  >
    <span class="toggle-switch-track"><span class="toggle-switch-thumb" /></span>
    <span class="toggle-switch-label">{{ label }}</span>
  </button>
</template>
