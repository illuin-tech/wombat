<script setup lang="ts">
import { computed, ref } from 'vue'
import { displayName, type AssetInfo, type EnvironmentInfo } from '@/domain/environment'
import type { ModuleMetadata } from '@/domain/module'
import Modal from '@/components/Modal.vue'
import { copyCandidates } from '@/pages/simulations/model/copyCandidates'

/** Picks the asset to copy into a simulation, among every environment's, grouped by environment. */
const props = defineProps<{ open: boolean; environments: EnvironmentInfo[] | null; modules: ModuleMetadata[]; error?: string | null }>()
const emit = defineEmits<{ close: []; pick: [asset: AssetInfo] }>()

const search = ref('')
const groups = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return (props.environments ?? [])
    .map(environment => ({
      environment,
      candidates: copyCandidates(environment.assets, props.modules)
        .filter(candidate => !needle || [candidate.asset.id, candidate.asset.name].some(text => text?.toLowerCase().includes(needle))),
    }))
    .filter(group => group.candidates.length > 0)
})
</script>

<template>
  <Modal :open="open" title="Copy an asset" @close="emit('close')">
    <div class="sim-copy-picker">
      <p class="page-desc">The copy is a snapshot: change it freely, and sync it with its source when that one changes.</p>
      <input v-model="search" type="search" class="search-input" placeholder="Filter by name" aria-label="Filter assets by name" />
      <span v-if="error" class="form-error" role="alert">{{ error }}</span>
      <p v-else-if="!environments" class="form-hint">Loading the environments…</p>
      <p v-else-if="groups.length === 0" class="form-hint">No asset matches.</p>
      <section v-for="group in groups" :key="group.environment.id" class="sim-copy-group">
        <span class="card-section-label">{{ displayName(group.environment) }}</span>
        <button
          v-for="candidate in group.candidates"
          :key="candidate.asset.id"
          class="sim-copy-option"
          type="button"
          :disabled="candidate.blocked !== null"
          @click="emit('pick', candidate.asset)"
        >
          <span>{{ displayName(candidate.asset) }}</span>
          <code class="asset-card-id">{{ candidate.asset.id }}</code>
          <span v-if="candidate.blocked" class="status-chip">{{ candidate.blocked }}</span>
        </button>
      </section>
    </div>
  </Modal>
</template>
