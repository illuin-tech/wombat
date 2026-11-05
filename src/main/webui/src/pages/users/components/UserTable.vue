<script setup lang="ts">
import type { UserInfo } from '@/domain/access'
import { asUserDate } from '@/services/format/formatters'
import DataTable from '@/components/DataTable.vue'
import UserRoleChip from '@/pages/users/components/UserRoleChip.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'

defineProps<{users: UserInfo[]; canManage: boolean; pending?: boolean; loading?: boolean}>()
const skeletonRows = [0, 1, 2, 3, 4]
const emit = defineEmits<{ grant: [user: UserInfo, role: string]; remove: [user: UserInfo, role: string] }>()
</script>

<template>
  <DataTable :title="loading ? 'Users' : `${users.length} ${users.length === 1 ? 'user' : 'users'}`">
    <table>
      <thead><tr><th>Name</th><th>Email</th><th>Roles</th><th v-if="canManage">Grant</th><th>First seen</th><th>Last seen</th></tr></thead>
      <tbody>
        <template v-if="loading">
          <tr v-for="row in skeletonRows" :key="row">
            <td v-for="cell in (canManage ? 6 : 5)" :key="cell"><SkeletonBlock :width="cell === 1 ? '110px' : '72px'" height="12px"/></td>
          </tr>
        </template>
        <template v-else>
        <tr v-for="user in users" :key="user.id">
          <td class="name">{{ user.name }}<span v-if="user.self" class="user-self">you</span></td>
          <td :class="{ muted: !user.email }">{{ user.email ?? '—' }}</td>
          <td>
            <div class="user-roles">
              <UserRoleChip v-for="role in user.roles" :key="role.role" :role="role" :user-name="user.name" :disabled="pending" @remove="emit('remove', user, $event)" />
              <span v-if="user.roles.length === 0" class="user-role none">no access</span>
            </div>
          </td>
          <td v-if="canManage"><div class="user-grants"><button v-for="role in user.grantableRoles" :key="role" class="user-grant" type="button" :disabled="pending" @click="emit('grant', user, role)">+ {{ role }}</button></div></td>
          <td>{{ asUserDate(user.createdAt) }}</td>
          <td>{{ asUserDate(user.lastSeenAt) }}</td>
        </tr>
        </template>
      </tbody>
    </table>
    <div v-if="!loading && users.length === 0" class="apikey-empty">No one has signed in yet.</div>
  </DataTable>
</template>
