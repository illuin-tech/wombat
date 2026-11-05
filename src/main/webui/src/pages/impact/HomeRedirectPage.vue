<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {fetchLiveEnvironments} from '@/services/api/environments'
import {legacyRedirect} from '@/pages/impact/model/useDashboardQuery'
import NoticeCard from '@/components/NoticeCard.vue'

const route = useRoute()
const router = useRouter()
const error = ref(false)

onMounted(async () => {
  try {
    const environments = await fetchLiveEnvironments()
    const destination = legacyRedirect(route.query, environments.map(environment => environment.id))
    if (destination) await router.replace(destination)
    else error.value = true
  } catch {
    error.value = true
  }
})
</script>

<template>
  <div class="main">
    <NoticeCard v-if="error" title="No environments available" tone="error">The dashboard could not find an environment to display.</NoticeCard>
    <p v-else class="loading-state">Loading environments…</p></div>
</template>