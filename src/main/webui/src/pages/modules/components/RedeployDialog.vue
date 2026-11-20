<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { shortDeploymentId, type CoreDeploymentStatus, type Redeployed } from '@/domain/module'
import { errorText } from '@/services/api/client'
import { fetchDeployment, redeployCore } from '@/services/api/modules'
import Modal from '@/components/Modal.vue'
import { awaitConvergence, type Convergence } from '@/pages/modules/model/deployment'

const props = defineProps<{ open: boolean; status: CoreDeploymentStatus }>()
const emit = defineEmits<{ close: []; deployed: [] }>()

type Phase = 'confirm' | 'deploying' | 'converging' | 'finished'

const phase = ref<Phase>('confirm')
const error = ref<string | null>(null)
const redeployed = ref<Redeployed | null>(null)
const progress = ref<Convergence | null>(null)
let watching: AbortController | null = null

const added = computed(() => props.status.pending.filter(module => module.enabled).map(module => module.type))
const removed = computed(() => props.status.pending.filter(module => !module.enabled).map(module => module.type))
const shortId = computed(() => redeployed.value ? shortDeploymentId(redeployed.value.id) : '')

watch(() => props.open, open => {
  if (!open) {
    watching?.abort()
    return
  }
  phase.value = 'confirm'
  error.value = null
  redeployed.value = null
  progress.value = null
})

async function redeploy(): Promise<void> {
  phase.value = 'deploying'
  error.value = null
  try {
    redeployed.value = await redeployCore()
  } catch (caught) {
    error.value = errorText(caught)
    phase.value = 'confirm'
    return
  }
  emit('deployed')
  phase.value = 'converging'
  watching = new AbortController()
  try {
    progress.value = await awaitConvergence(fetchDeployment, redeployed.value.version, {
      onProgress: current => { progress.value = current },
      signal: watching.signal,
    })
  } catch (caught) {
    error.value = errorText(caught)
  }
  phase.value = 'finished'
}

function close(): void {
  /* The redeploy is recorded once sent: closing only stops watching the instances */
  if (phase.value !== 'deploying') emit('close')
}
</script>

<template>
  <Modal :open="open" title="Redeploy the core" @close="close">
    <div class="redeploy-dialog">
      <template v-if="phase === 'confirm' || phase === 'deploying'">
        <p class="confirm-dialog-text">Every instance rebuilds its core with the modules enabled now.</p>
        <ul v-if="added.length > 0 || removed.length > 0" class="redeploy-changes">
          <li v-for="type in added" :key="type"><span class="module-state enabled">Starts</span> <code class="tech-name">{{ type }}</code></li>
          <li v-for="type in removed" :key="type"><span class="module-state disabled">Stops</span> <code class="tech-name">{{ type }}</code></li>
        </ul>
        <p v-else class="form-hint">No module changes: a redeploy still resets the evaluation caches.</p>
        <p class="form-hint">
          Cached evaluations are reset and monitoring pauses briefly. The other instances follow within
          {{ status.pollIntervalSeconds }} s.
        </p>
        <span v-if="error" class="form-error" role="alert">{{ error }}</span>
        <div class="form-actions">
          <button class="btn-apply" type="button" :disabled="phase === 'deploying'" @click="redeploy">
            {{ phase === 'deploying' ? 'Redeploying…' : 'Redeploy' }}
          </button>
          <button class="btn-reset" type="button" :disabled="phase === 'deploying'" @click="close">Cancel</button>
        </div>
      </template>

      <template v-else>
        <p v-if="redeployed?.local.status === 'failed'" class="form-error" role="alert">
          This instance could not build core <code class="core-id">{{ shortId }}</code> and keeps running the previous one: {{ redeployed.local.error }}
        </p>
        <p v-else class="confirm-dialog-text">This instance runs core <code class="core-id">{{ shortId }}</code>.</p>

        <p v-if="phase === 'converging'" class="form-hint" role="status">
          Waiting for the other instances<template v-if="progress">: {{ progress.converged }} of {{ progress.total }} run it</template>…
        </p>
        <template v-else-if="progress">
          <p v-if="progress.state === 'done'" class="confirm-dialog-text" role="status">Every instance runs core <code class="core-id">{{ shortId }}</code>.</p>
          <p v-else-if="progress.state === 'failed'" class="form-error" role="alert">
            {{ progress.failed.map(instance => instance.instanceId).join(', ') }} could not build it, and keep{{ progress.failed.length === 1 ? 's' : '' }}
            running the previous core: {{ progress.failed[0].error }}
          </p>
          <p v-else class="form-error" role="alert">
            {{ progress.total - progress.converged }} instance{{ progress.total - progress.converged === 1 ? ' has' : 's have' }} not followed yet:
            check the instances on the Modules page.
          </p>
        </template>
        <span v-if="error" class="form-error" role="alert">{{ error }}</span>

        <div class="form-actions">
          <button class="btn-reset" type="button" @click="close">{{ phase === 'finished' ? 'Close' : 'Stop watching' }}</button>
        </div>
      </template>
    </div>
  </Modal>
</template>
