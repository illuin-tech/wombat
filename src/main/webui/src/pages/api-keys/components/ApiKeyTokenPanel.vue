<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{ token: string }>()
const copied = ref(false)
const usage = computed(() => `curl -H "X-API-Key: ${props.token}" ${window.location.origin}/api/environments`)

async function copyToken(): Promise<void> {
  try {
    await navigator.clipboard.writeText(props.token)
  } catch {
    const input = document.createElement('textarea')
    input.value = props.token
    input.style.position = 'fixed'
    input.style.opacity = '0'
    document.body.append(input)
    input.select()
    document.execCommand('copy')
    input.remove()
  }
  copied.value = true
  window.setTimeout(() => copied.value = false, 1500)
}
</script>

<template>
  <div class="apikey-token" aria-live="polite">
    <div class="apikey-token-title">Copy your new key now: it won't be shown again</div>
    <div class="apikey-token-row">
      <code class="apikey-token-value">{{ token }}</code>
      <button class="btn-reset" type="button" @click="copyToken">{{ copied ? 'Copied' : 'Copy' }}</button>
    </div>
    <pre class="apikey-token-usage">{{ usage }}</pre>
  </div>
</template>
