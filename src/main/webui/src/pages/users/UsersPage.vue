<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { UserInfo } from '@/domain/access'
import { fetchUsers, grantRole, removeRole } from '@/services/api/users'
import { loadAppInfo, useAppInfo } from '@/services/auth/session'
import { useAsync } from '@/services/tools/useAsync'
import UserTable from '@/pages/users/components/UserTable.vue'
import NoticeCard from '@/components/NoticeCard.vue'
import PageHead from '@/layout/PageHead.vue'

const { appInfo } = useAppInfo()
const userList = useAsync(fetchUsers)
const users = userList.data
const pending = ref(false)
const error = ref('')
const canManage = computed(() => appInfo.value?.user.permissions.includes('api.user.manage') ?? false)
const tableLoading = computed(() => !appInfo.value || userList.loading.value)

function errorText(caught: unknown, fallback: string): string {
  return caught instanceof Error ? caught.message : fallback
}

async function load(): Promise<void> {
  try {
    const info = await loadAppInfo()
    if (info.auth.enabled) {
      await userList.run()
      if (userList.error.value) error.value = errorText(userList.error.value, 'Users could not be loaded.')
    }
  } catch (caught) {
    error.value = errorText(caught, 'Users could not be loaded.')
  }
}

async function grant(user: UserInfo, role: string): Promise<void> {
  if (!window.confirm(`Grant the ${role} role to ${user.name}?`)) return
  await changeRole(user, role, 'grant')
}

async function remove(user: UserInfo, role: string): Promise<void> {
  if (!window.confirm(`Remove the ${role} role granted to ${user.name}?`)) return
  await changeRole(user, role, 'remove')
}

async function changeRole(user: UserInfo, role: string, operation: 'grant' | 'remove'): Promise<void> {
  pending.value = true
  error.value = ''
  try {
    if (operation === 'grant') await grantRole(user.id, role)
    else await removeRole(user.id, role)
    await userList.run()
    if (userList.error.value) error.value = errorText(userList.error.value, 'The role was changed, but the users list could not be refreshed.')
  } catch (caught) {
    error.value = errorText(caught, 'The role could not be changed.')
  } finally {
    pending.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="main">
    <PageHead title="Users">
      Everyone who signed in to Wombat. A user's roles come from the identity provider and the <code>auth.roles.*</code> settings
      (as of their last visit, refreshed at most every 5 minutes), plus the roles granted here, which apply at their next request.
      Roles from the provider or the settings can only be changed there.
    </PageHead>
    <NoticeCard v-if="appInfo && !appInfo.auth.enabled" title="Authentication is disabled">
      Users are only recorded with authentication: start Wombat with the <code>oidc</code> profile (see doc/access-control.md).
    </NoticeCard>
    <template v-else>
      <NoticeCard v-if="error || userList.error.value" title="Users error" tone="error">{{ error || errorText(userList.error.value, 'Users could not be loaded.') }}</NoticeCard>
      <UserTable :users="users ?? []" :can-manage="canManage" :pending="pending" :loading="tableLoading" @grant="grant" @remove="remove" />
    </template>
  </div>
</template>
