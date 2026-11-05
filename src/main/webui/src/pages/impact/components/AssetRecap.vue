<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import type {AssetPanel, DashboardModel, KubernetesPanel as KubernetesPanelType, LLMPanel as LlmPanelType} from '@/pages/impact/model/aggregate'
import type {AssetInfo} from '@/domain/environment'
import KubernetesPanel from '@/pages/impact/components/KubernetesPanel.vue'
import LlmPanel from '@/pages/impact/components/LlmPanel.vue'
import Modal from '@/components/Modal.vue'
import UnrecognizedAssetsModal from '@/pages/impact/components/UnrecognizedAssetsModal.vue'
import SkeletonBlock from '@/components/SkeletonBlock.vue'

const props = defineProps<{model?: DashboardModel | null; assets: AssetInfo[]; loading?: boolean; refreshing?: boolean}>()
const recap = ref<HTMLElement | null>(null)
const expanded = ref<string | null>(null)
const showUnrecognized = ref(false)
const unrecognized = computed(() => props.assets)
const selected = computed(() => props.model?.assetPanels.find(panel => panelKey(panel) === expanded.value))
let observer: ResizeObserver | null = null

function panelKey(panel: AssetPanel): string {
  return `${panel.kind}:${panel.name}`
}

function openPanel(panel: AssetPanel): void {
  expanded.value = panelKey(panel)
}

function syncHeight(): void {
  const chart = recap.value?.closest('.overview-row')?.querySelector('.chart-card')
  if (recap.value && chart) recap.value.style.height = `${chart.getBoundingClientRect().height}px`
}

onMounted(() => {
  const chart = recap.value?.closest('.overview-row')?.querySelector('.chart-card')
  syncHeight()
  if (chart && typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(syncHeight)
    observer.observe(chart)
  }
  window.addEventListener('resize', syncHeight)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  window.removeEventListener('resize', syncHeight)
})

function kubernetes(panel: AssetPanel): panel is KubernetesPanelType {
  return panel.kind === 'kubernetes'
}

function llm(panel: AssetPanel): panel is LlmPanelType {
  return panel.kind === 'llm'
}
</script>

<template>
  <div ref="recap" class="kpi-card yellow" :class="{'is-refreshing': refreshing}">
    <button v-if="!loading && unrecognized.length" type="button" class="recap-warning-btn" id="unrecognizedAssetsTrigger" title="Unrecognized assets detected" aria-label="Unrecognized assets detected" @click="showUnrecognized = true">
      <svg width="15" height="15" viewBox="0 0 16 16" fill="none"><path d="M8 1L15.5 14H0.5L8 1Z" fill="#ea580c" stroke="#ea580c" stroke-width="1" stroke-linejoin="round"/><path d="M8 5.5V9.5" stroke="#ffffff" stroke-width="1.5" stroke-linecap="round"/><circle cx="8" cy="11.75" r="0.75" fill="#ffffff"/></svg>
    </button>
    <div v-if="loading" class="stat-row">
      <div v-for="item in 4" :key="item" class="stat"><div class="kpi-label"><SkeletonBlock width="96px" height="12px"/></div><div class="kpi-value"><SkeletonBlock width="54px" height="24px"/></div></div>
    </div>
    <div v-else-if="model" class="stat-row">
      <div class="stat"><div class="kpi-label">Assets</div><div class="kpi-value green">{{ model.assetCount }}</div></div>
      <div class="stat"><div class="kpi-label">Services tracked</div><div class="kpi-value yellow">{{ model.serviceCount }}</div></div>
      <div class="stat"><div class="kpi-label"><img class="svc-icon" src="/icons/docker.svg" alt="container"/>Docker services</div><div class="kpi-value blue">{{ model.dockerServiceCount }}</div></div>
      <div class="stat"><div class="kpi-label"><img class="svc-icon" src="/icons/llm.svg" alt="llm"/>LLM services</div><div class="kpi-value purple">{{ model.llmServiceCount }}</div></div>
    </div>
    <div v-if="loading" class="asset-breakdown">
      <SkeletonBlock v-for="item in 3" :key="item" height="42px"/>
    </div>
    <div v-else-if="model" class="asset-breakdown">
      <template v-for="panel in model.assetPanels" :key="panelKey(panel)">
        <KubernetesPanel v-if="kubernetes(panel)" :panel="panel" @expand="openPanel(panel)" />
        <LlmPanel v-else-if="llm(panel)" :panel="panel" @expand="openPanel(panel)" />
      </template>
    </div>
  </div>
  <Modal :open="selected !== undefined" :title="selected?.name ?? ''" overlay-id="assetModal" title-id="assetModalTitle" close-id="assetModalClose" @close="expanded = null">
    <KubernetesPanel v-if="selected && kubernetes(selected)" :panel="selected" modal />
    <LlmPanel v-else-if="selected && llm(selected)" :panel="selected" modal />
  </Modal>
  <UnrecognizedAssetsModal v-if="unrecognized.length" :open="showUnrecognized" :assets="unrecognized" @close="showUnrecognized = false" />
</template>
