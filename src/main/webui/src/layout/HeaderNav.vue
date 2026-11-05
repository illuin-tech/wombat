<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { settingsLinks } from '@/services/auth/permissions'
import { useAppInfo } from '@/services/auth/session'
import { useDropdown } from '@/layout/useDropdown'

const { appInfo } = useAppInfo()
const route = useRoute()
const links = computed(() => settingsLinks(appInfo.value?.user))
const onReport = computed(() => route.name === 'home' || route.name === 'impact')
const inSettings = computed(() => links.value.some(link => link.to === route.path))
const { open, root } = useDropdown()
</script>

<template>
  <nav v-if="links.length > 0" class="header-nav" aria-label="Main">
    <RouterLink class="header-nav-link" to="/" :aria-current="onReport ? 'page' : undefined">Report</RouterLink>
    <div ref="root" class="header-nav-menu" :class="{ open }" id="settingsMenu">
      <button
        class="header-nav-link header-nav-trigger"
        :class="{ current: inSettings }"
        id="settingsMenuTrigger"
        type="button"
        aria-haspopup="menu"
        :aria-expanded="open"
        aria-controls="settingsMenuDropdown"
        @click="open = !open"
      >
        Settings
        <svg class="header-nav-chevron" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>
      <div class="header-nav-dropdown" id="settingsMenuDropdown" role="menu">
        <RouterLink v-for="link in links" :key="link.to" class="header-nav-item" :to="link.to" role="menuitem" @click="open = false">
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
      </div>
    </div>
  </nav>
</template>
