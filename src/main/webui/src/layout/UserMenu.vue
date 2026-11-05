<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { menuLinks } from '@/services/auth/permissions'
import { useAppInfo } from '@/services/auth/session'

const { appInfo } = useAppInfo()
const user = computed(() => appInfo.value?.user ?? null)
const links = computed(() => menuLinks(user.value))
const open = ref(false)
const root = ref<HTMLElement | null>(null)

function onDocumentClick(event: MouseEvent): void {
  if (root.value && !root.value.contains(event.target as Node))
    open.value = false
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape')
    open.value = false
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
})
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
      <span class="header-user-name" :title="user.name ?? ''">{{ user.name }}</span>
      <svg class="header-user-chevron" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <path d="M6 9l6 6 6-6" />
      </svg>
    </button>
    <div class="header-user-menu" id="userMenuDropdown" role="menu">
      <div class="header-user-identity">
        <span class="header-user-label">Signed in as</span>
        <span class="header-user-full-name">{{ user.name }}</span>
      </div>
      <div class="header-user-roles">
        <span class="header-user-label">Role</span>
        <span class="header-user-role-list">
          <span v-for="role in user.roles" :key="role" class="header-user-role">{{ role }}</span>
        </span>
      </div>
      <RouterLink v-for="link in links" :key="link.to" class="header-menu-link" :to="link.to" role="menuitem" @click="open = false">
        <svg v-if="link.to === '/users'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
          <path d="M16 3.13a4 4 0 0 1 0 7.75" />
        </svg>
        <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="7.5" cy="15.5" r="5.5" />
          <path d="M21 2l-9.6 9.6" />
          <path d="M15.5 7.5l3 3L22 7l-3-3" />
        </svg>
        {{ link.label }}
      </RouterLink>
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
