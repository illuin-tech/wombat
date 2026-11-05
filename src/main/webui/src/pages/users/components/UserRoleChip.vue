<script setup lang="ts">
import type { UserRole } from '@/domain/access'
import { roleSourceText } from '@/pages/users/roleSource'

defineProps<{ role: UserRole; userName: string; disabled?: boolean }>()
const emit = defineEmits<{ remove: [role: string] }>()
</script>

<template>
  <span class="user-role" :class="{ granted: role.grant !== null }" :title="roleSourceText(role)">
    {{ role.role }}
    <button v-if="role.removable" class="user-role-remove" type="button" :disabled="disabled" :aria-label="`Remove role ${role.role} from ${userName}`" @click.stop="emit('remove', role.role)">&times;</button>
    <svg v-else-if="!role.grant" class="user-role-lock" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg>
  </span>
</template>
