<script setup lang="ts">
import { computed } from 'vue'
import { shortDeploymentId, type CoreDeploymentStatus } from '@/domain/module'
import { asUserDate } from '@/services/format/formatters'
import { convergence, divergentInstances } from '@/pages/modules/model/deployment'

const props = defineProps<{ status: CoreDeploymentStatus }>()

const latest = computed(() => props.status.latest)
const progress = computed(() => convergence(props.status, latest.value.version))
const divergent = computed(() => divergentInstances(props.status.instances))
</script>

<template>
  <div class="core-status">
    <div class="core-status-line">
      <span class="core-status-label">Core</span>
      <code class="core-id" :title="`${latest.id} (#${latest.version})`">{{ shortDeploymentId(latest.id) }}</code>
      <span class="core-status-meta">
        deployed {{ asUserDate(latest.requestedAt) }}<template v-if="latest.requestedBy"> by {{ latest.requestedBy }}</template>
      </span>
    </div>

    <details class="core-instances">
      <summary>
        {{ progress.converged }}/{{ progress.total }} instance{{ progress.total === 1 ? '' : 's' }} on <code class="core-id">{{ shortDeploymentId(latest.id) }}</code>
        <span v-if="progress.failed.length > 0" class="module-state missing">{{ progress.failed.length }} failed</span>
      </summary>
      <ul>
        <li v-for="instance in status.instances" :key="instance.instanceId" class="core-instance">
          <code class="tech-name">{{ instance.instanceId }}</code>
          <span v-if="instance.current" class="core-status-meta">(serving this page)</span>
          <span class="core-status-meta">
            runs <code class="core-id" :title="instance.deployedId ?? undefined">{{ instance.deployedId ? shortDeploymentId(instance.deployedId) : '?' }}</code> #{{ instance.deployedVersion }}
          </span>
          <span class="module-state" :class="instance.status === 'ok' ? 'enabled' : 'missing'">{{ instance.status === 'ok' ? 'OK' : 'Failed' }}</span>
          <span v-if="instance.error" class="form-error">{{ instance.error }}</span>
          <span v-if="instance.missingModules.length > 0" class="core-status-meta">
            Lacks <code v-for="type in instance.missingModules" :key="type" class="tech-name">{{ type }}</code>
          </span>
        </li>
      </ul>
    </details>

    <p v-if="divergent.length > 0" class="form-hint core-divergence" role="status">
      {{ divergent.map(instance => instance.instanceId).join(', ') }} detect{{ divergent.length === 1 ? 's' : '' }} other modules than this
      instance: their extension folders differ.
    </p>
  </div>
</template>
