<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ApiKeyInfo } from '@/domain/access'
import { apiKeyStatus, filterApiKeys } from '@/pages/api-keys/apiKeyExpiry'
import DataTable from '@/components/DataTable.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'

const props = defineProps<{keys: ApiKeyInfo[]; loading?: boolean}>()
const emit = defineEmits<{ revoke: [key: ApiKeyInfo] }>()
const skeletonRows = [0, 1, 2, 3, 4]
const search = ref('')
const activeOnly = ref(true)
const shown = computed(() => filterApiKeys(props.keys, search.value, activeOnly.value))
const dateFormatter = new Intl.DateTimeFormat(undefined, { year: 'numeric', month: 'short', day: 'numeric', timeZone: 'UTC' })

function formatDate(value: string | null): string {
  return value ? dateFormatter.format(new Date(value)) : '—'
}

function statusLabel(key: ApiKeyInfo): string {
  const status = apiKeyStatus(key)
  return `${status[0].toUpperCase()}${status.slice(1)}`
}

function requestRevoke(key: ApiKeyInfo): void {
  if (window.confirm(`Revoke "${key.name}"? Anything using it will stop working immediately.`)) emit('revoke', key)
}
</script>

<template>
  <DataTable title="Keys">
    <template #controls>
      <input v-model="search" type="search" class="apikey-search" placeholder="Filter by name or creator" aria-label="Filter keys by name or creator" />
      <label class="apikey-check"><input v-model="activeOnly" type="checkbox" /><span>Active only</span></label>
    </template>
    <table>
      <thead><tr><th>Name</th><th>Permissions</th><th>Created by</th><th>Created</th><th>Expires</th><th>Last used</th><th>Status</th><th></th></tr></thead>
      <tbody>
        <template v-if="loading">
          <tr v-for="row in skeletonRows" :key="row">
            <td v-for="cell in 8" :key="cell"><SkeletonBlock :width="cell === 1 ? '100px' : '64px'" height="12px"/></td>
          </tr>
        </template>
        <template v-else>
        <tr v-for="key in shown" :key="key.id" :class="{ inactive: apiKeyStatus(key) !== 'active' }">
          <td class="name">{{ key.name }}</td>
          <td><div class="apikey-perms"><span v-for="permission in key.permissions" :key="permission" class="apikey-perm">{{ permission }}</span></div></td>
          <td>{{ key['created-by'] ?? '—' }}</td>
          <td>{{ formatDate(key['created-at']) }}</td>
          <td>{{ key['expires-at'] ? formatDate(key['expires-at']) : 'Never' }}</td>
          <td>{{ formatDate(key['last-used-at']) }}</td>
          <td><span class="apikey-status" :class="apiKeyStatus(key)">{{ statusLabel(key) }}</span></td>
          <td><button v-if="apiKeyStatus(key) === 'active'" class="btn-danger" type="button" @click="requestRevoke(key)">Revoke</button></td>
        </tr>
        </template>
      </tbody>
    </table>
    <div v-if="!loading && shown.length === 0" class="apikey-empty">{{ keys.length === 0 ? 'No keys yet.' : 'No key matches these filters.' }}</div>
  </DataTable>
</template>
