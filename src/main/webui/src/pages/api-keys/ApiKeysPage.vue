<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { ApiKeyInfo } from '@/domain/access'
import { fetchApiKeySettings, fetchApiKeys, revokeApiKey } from '@/services/api/apiKeys'
import { loadAppInfo, useAppInfo } from '@/services/auth/session'
import { can } from '@/services/auth/permissions'
import { useAsync } from '@/services/tools/useAsync'
import ApiKeyForm from '@/pages/api-keys/components/ApiKeyForm.vue'
import ApiKeyTable from '@/pages/api-keys/components/ApiKeyTable.vue'
import ApiKeyTokenPanel from '@/pages/api-keys/components/ApiKeyTokenPanel.vue'
import NoticeCard from '@/components/NoticeCard.vue'
import PageHead from '@/layout/PageHead.vue'

const { appInfo } = useAppInfo()
const settingsRequest = useAsync(fetchApiKeySettings)
const keysRequest = useAsync(fetchApiKeys)
const settings = settingsRequest.data
const keys = keysRequest.data
const token = ref<string | null>(null)
const showAll = ref(false)
const canViewAll = computed(() => can(appInfo.value?.user, 'api.apikey.admin'))
const mutationError = ref('')
const tableLoading = computed(() => !appInfo.value || keysRequest.loading.value)
const error = computed(() => mutationError.value
  || (settingsRequest.error.value instanceof Error ? settingsRequest.error.value.message : '')
  || (keysRequest.error.value instanceof Error ? keysRequest.error.value.message : ''))

function errorText(caught: unknown, fallback: string): string {
  return caught instanceof Error ? caught.message : fallback
}

async function load(): Promise<void> {
  try {
    const info = await loadAppInfo()
    if (!info.auth.enabled) return
    void settingsRequest.run()
    void keysRequest.run(showAll.value)
  } catch (caught) {
    mutationError.value = errorText(caught, 'API keys could not be loaded.')
  }
}

async function refreshKeys(): Promise<void> {
  mutationError.value = ''
  await keysRequest.run(showAll.value)
  if (keysRequest.error.value) mutationError.value = errorText(keysRequest.error.value, 'The API key list could not be refreshed.')
}

async function created(key: ApiKeyInfo): Promise<void> {
  token.value = key.token ?? null
  await refreshKeys()
}

async function revoke(key: ApiKeyInfo): Promise<void> {
  mutationError.value = ''
  try {
    await revokeApiKey(key.id)
    await refreshKeys()
  } catch (caught) {
    mutationError.value = errorText(caught, 'The API key could not be revoked.')
  }
}

onMounted(load)
watch(showAll, refreshKeys)
</script>

<template>
  <div class="main">
    <PageHead title="API keys">
      Keys let your scripts and CI call the Wombat API on your behalf, sent in the <code>X-API-Key</code> header.
      A key stops working when it expires, when it is revoked, or when you lose the roles behind its permissions.
    </PageHead>
    <NoticeCard v-if="appInfo && !appInfo.auth.enabled" title="Authentication is disabled">
      API keys need authentication: start Wombat with the <code>oidc</code> profile (see doc/access-control.md).
    </NoticeCard>
    <template v-else>
      <NoticeCard v-if="error" title="API key error" tone="error">{{ error }}</NoticeCard>
      <ApiKeyForm v-if="settings" :settings="settings" @created="created" />
      <ApiKeyTokenPanel v-if="token" :token="token" />
      <ApiKeyTable v-model:show-all="showAll" :keys="keys ?? []" :loading="tableLoading" :can-view-all="canViewAll" @revoke="revoke" />
    </template>
  </div>
</template>
