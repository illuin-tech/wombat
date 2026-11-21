<script setup lang="ts">
import { PERIOD_LABELS, PERIODS, type SimulationPeriod } from '@/domain/simulation'
import Modal from '@/components/Modal.vue'

defineProps<{ open: boolean; canEdit: boolean }>()
defineEmits<{ close: [] }>()
const referenceWindow = defineModel<SimulationPeriod>('referenceWindow', { required: true })
</script>

<template>
  <Modal :open="open" title="Evaluation settings" @close="$emit('close')">
    <div class="sim-settings">
      <label class="form-field">
        <span class="filter-label">Reference window</span>
        <select v-model="referenceWindow" :disabled="!canEdit" id="referenceWindow">
          <option v-for="candidate in PERIODS" :key="candidate" :value="candidate">{{ PERIOD_LABELS[candidate] }}</option>
        </select>
        <span class="form-hint">{{ canEdit ? 'Saved with the simulation.' : 'Only the simulation\'s managers can change it.' }}</span>
      </label>

      <section class="sim-settings-section">
        <h3 class="sim-settings-title">How the results are computed</h3>
        <p>
          A simulation is evaluated once, over its reference window: the last day up to the current hour, or the last
          week, month (30 days) or year (365 days) up to midnight UTC. Each result is then scaled to the period shown, in
          proportion to its length: from a day, a week is 7 times that day and a year 365 times.
        </p>
      </section>

      <section class="sim-settings-section">
        <h3 class="sim-settings-title">How the window changes them</h3>
        <ul>
          <li>
            Copies of monitored assets read the activity recorded over the window. A short window follows the latest
            activity, but whatever happened in it, a peak, a quiet weekend or an outage, is scaled up as if it lasted
            the whole period. A longer window smooths those out, and follows recent changes more slowly.
          </li>
          <li>
            Where the window has no recorded activity, it counts as none, which lowers the results. The table tells when
            an asset's activity covers only part of the window: a copy of an asset monitored for a few days is better
            evaluated over a day or a week.
          </li>
          <li>
            Modeled assets compute their activity from their scenarios, so the window only matters when a scenario
            varies over time: a window shorter than its cycle catches only part of it.
          </li>
          <li>The window doesn't change the number of calls made to Boavizta or Ecologits: each asset is evaluated once.</li>
        </ul>
      </section>
    </div>
  </Modal>
</template>
