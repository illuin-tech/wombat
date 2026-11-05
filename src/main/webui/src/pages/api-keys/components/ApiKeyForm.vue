<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { createApiKey, type ApiKeyInfo, type ApiKeySettings } from '@/services/api/apiKeys'
import { apiKeyExpiryBounds, clampApiKeyExpiry } from '@/pages/api-keys/apiKeyExpiry'

const props = defineProps<{ settings: ApiKeySettings }>()
const emit = defineEmits<{ created: [key: ApiKeyInfo] }>()
const name = ref('')
const permissions = ref<string[]>([])
const expiresOn = ref('')
const neverExpires = ref(false)
const saving = ref(false)
const error = ref('')
const bounds = computed(() => apiKeyExpiryBounds(new Date(), props.settings.maxLifetimeDays))

watch(() => props.settings, (settings) => {
  permissions.value = [...settings.grantablePermissions]
  expiresOn.value = bounds.value.defaultValue
}, { immediate: true })

async function submit(): Promise<void> {
  error.value = ''
  if (permissions.value.length === 0) {
    error.value = 'Pick at least one permission.'
    return
  }
  saving.value = true
  try {
    const expiry = neverExpires.value ? null : `${clampApiKeyExpiry(expiresOn.value, bounds.value)}T00:00:00Z`
    const key = await createApiKey({ name: name.value.trim(), permissions: permissions.value, expiresAt: expiry })
    name.value = ''
    emit('created', key)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : 'The API key could not be created.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form class="chart-card apikey-form" autocomplete="off" @submit.prevent="submit">
    <div class="chart-title">New key</div>
    <label class="apikey-field">
      <span class="filter-label">Name</span>
      <input v-model="name" type="text" maxlength="100" placeholder="e.g. ci-pipeline" required />
    </label>
    <div class="apikey-field">
      <span class="filter-label">Permissions</span>
      <div class="apikey-checks">
        <label v-for="permission in settings.grantablePermissions" :key="permission" class="apikey-check">
          <input v-model="permissions" type="checkbox" :value="permission" />
          <span>{{ permission }}</span>
        </label>
      </div>
    </div>
    <div class="apikey-field">
      <span class="filter-label">Expires on (UTC)</span>
      <div class="apikey-expiry">
        <input v-model="expiresOn" type="date" :min="bounds.min" :max="bounds.max" :required="!neverExpires" :disabled="neverExpires" />
        <label v-if="settings.allowNeverExpiring" class="apikey-check"><input v-model="neverExpires" type="checkbox" /><span>Never expires</span></label>
      </div>
      <span class="apikey-hint">At most {{ settings.maxLifetimeDays }} days from today.</span>
    </div>
    <div class="apikey-actions">
      <button class="btn-apply" type="submit" :disabled="saving">{{ saving ? 'Creating…' : 'Create key' }}</button>
      <span v-if="error" class="apikey-error" role="alert">{{ error }}</span>
    </div>
  </form>
</template>
