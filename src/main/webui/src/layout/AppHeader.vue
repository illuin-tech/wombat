<script setup lang="ts">
import { useTheme } from '@/layout/theme/useTheme'

const { isDark, toggleTheme } = useTheme()
</script>

<template>
  <div class="header">
    <a class="header-home" href="/" title="Back to the report">
      <img src="/icons/wombat-large-mask.png" alt="Wombat" class="header-logo" />
      <h1>Wombat — <span>Environmental Impact</span></h1>
    </a>
    <slot name="nav" />
    <slot name="headerExtra" />
    <slot name="userMenu" />
    <button
      id="themeToggle"
      class="theme-toggle"
      type="button"
      aria-label="Toggle dark mode"
      :aria-pressed="isDark"
      @click="toggleTheme"
    >
      <svg class="icon-sun" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
      </svg>
      <svg class="icon-moon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
      </svg>
    </button>
  </div>
</template>
