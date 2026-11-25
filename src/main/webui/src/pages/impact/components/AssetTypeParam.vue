<script setup lang="ts">
import {computed} from 'vue'
import type {AssetType} from '@/domain/environment'
import {can} from '@/services/auth/permissions'
import {useAppInfo} from '@/services/auth/session'

defineProps<{type: AssetType}>()

const {appInfo} = useAppInfo()
const canReadModules = computed(() => can(appInfo.value?.user, 'ui.modules.read'))
</script>

<template>
  <div class="param-item">
    <span class="param-key">Asset type</span>
    <span class="asset-type-name">
      <code class="tech-name">{{ type.name }}</code>
      <RouterLink v-if="canReadModules" class="asset-type-link" :to="{name: 'module', params: {type: type.name}}" title="See the module" aria-label="See the module">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <circle cx="11" cy="11" r="7" />
          <path d="m21 21-4.35-4.35" />
        </svg>
      </RouterLink>
    </span>
  </div>
</template>
