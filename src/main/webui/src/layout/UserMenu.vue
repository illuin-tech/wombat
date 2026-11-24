<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useAppInfo } from '@/services/auth/session'
import { useDropdown } from '@/layout/useDropdown'

const { appInfo } = useAppInfo()
const user = computed(() => appInfo.value?.user ?? null)
const { open, root } = useDropdown()

// Identity provider pictures can be missing or stop resolving: only show one that actually loaded.
const pictureFailed = ref(false)
watch(() => user.value?.picture, () => { pictureFailed.value = false })
const picture = computed(() => (pictureFailed.value ? null : user.value?.picture ?? null))
</script>

<template>
  <div v-if="user?.authenticated" ref="root" class="header-user" :class="{ open }" id="userMenu">
    <button
      class="header-user-trigger"
      id="userMenuTrigger"
      type="button"
      aria-haspopup="menu"
      :aria-expanded="open"
      aria-controls="userMenuDropdown"
      @click="open = !open"
    >
      <img v-if="picture" class="header-user-avatar" :src="picture" alt="" referrerpolicy="no-referrer" @error="pictureFailed = true" />
      <span class="header-user-name" :title="user.name ?? ''">{{ user.name }}</span>
      <svg class="header-user-chevron" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <path d="M6 9l6 6 6-6" />
      </svg>
    </button>
    <div class="header-user-menu" id="userMenuDropdown" role="menu">
      <div class="header-user-identity" :class="{ 'with-avatar': picture }">
        <img v-if="picture" class="header-user-avatar large" :src="picture" alt="" referrerpolicy="no-referrer" @error="pictureFailed = true" />
        <div class="header-user-identity-text">
          <span class="header-user-label">Signed in as</span>
          <span class="header-user-full-name">{{ user.name }}</span>
        </div>
      </div>
      <div class="header-user-roles">
        <span class="header-user-label">Role</span>
        <span class="header-user-role-list">
          <span v-for="role in user.roles" :key="role" class="header-user-role">{{ role }}</span>
        </span>
      </div>
      <a class="header-logout" href="/logout" role="menuitem">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
          <path d="M16 17l5-5-5-5" />
          <path d="M21 12H9" />
        </svg>
        Sign out
      </a>
    </div>
  </div>
</template>
