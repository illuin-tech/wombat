<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {RouterLink, useRoute, useRouter} from 'vue-router'
import {fetchLiveEnvironments} from '@/services/api/environments'
import {can} from '@/services/auth/permissions'
import {useAppInfo} from '@/services/auth/session'
import {legacyRedirect} from '@/pages/impact/model/useDashboardQuery'
import NoticeCard from '@/components/NoticeCard.vue'

const route = useRoute()
const router = useRouter()
const {appInfo} = useAppInfo()
/* `empty` is a fresh install, where environments are yet to be created */
const state = ref<'loading' | 'empty' | 'error'>('loading')
const canCreate = computed(() => can(appInfo.value?.user, 'ui.environments.manage') && can(appInfo.value?.user, 'api.environment.write'))

onMounted(async () => {
  try {
    const environments = await fetchLiveEnvironments()
    const destination = legacyRedirect(route.query, environments.map(environment => environment.id))
    if (destination) await router.replace(destination)
    /* Only an empty list has nowhere to go */
    else state.value = 'empty'
  } catch {
    state.value = 'error'
  }
})
</script>

<template>
  <div class="main">
    <NoticeCard v-if="state === 'empty'" title="No environments yet" tone="info">
      <template v-if="canCreate">
        Create an environment and its assets from the <RouterLink :to="{ name: 'environments' }">Environments</RouterLink> page to start tracking their impact.
      </template>
      <template v-else>Nothing is tracked yet: environments are created by the users allowed to manage them.</template>
    </NoticeCard>
    <NoticeCard v-else-if="state === 'error'" title="No environments available" tone="error">The dashboard could not find an environment to display.</NoticeCard>
    <p v-else class="loading-state">Loading environments…</p></div>
</template>
