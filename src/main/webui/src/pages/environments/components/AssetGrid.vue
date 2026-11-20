<script setup lang="ts">
import { REGIME_LABELS } from '@/domain/assetTypeDisplay'
import { assetFamily, assetTypeName, displayName, type AssetInfo, type AssetType } from '@/domain/environment'
import AssetTypeTag from '@/components/AssetTypeTag.vue'
import LockBadge from '@/components/LockBadge.vue'
import TimestampInfo from '@/components/TimestampInfo.vue'

const props = defineProps<{
  assets: AssetInfo[]
  typesByName: ReadonlyMap<string, AssetType>
  /** Whether the environment is provisioned, which locks all its assets. */
  locked: boolean
  canAdd: boolean
}>()
const emit = defineEmits<{ open: [asset: AssetInfo]; add: [] }>()

function typeLabel(asset: AssetInfo): string {
  const name = assetTypeName(asset)
  return (name && props.typesByName.get(name)?.localName) ?? name ?? 'Unknown type'
}

/** Left out when the regime is not known */
function regimeLabel(asset: AssetInfo): string | null {
  const name = assetTypeName(asset)
  const regime = name ? props.typesByName.get(name)?.regime : undefined
  return regime && regime !== 'UNKNOWN' ? REGIME_LABELS[regime] : null
}
</script>

<template>
  <div class="asset-grid">
    <button
      v-for="asset in assets"
      :key="asset.id"
      class="asset-card"
      :class="{ disabled: asset.disabledAt }"
      type="button"
      @click="emit('open', asset)"
    >
      <span class="asset-card-head">
        <span class="asset-card-name">{{ displayName(asset) }}</span>
        <LockBadge v-if="locked || asset.provisioned" label="" />
      </span>
      <code class="asset-card-id">{{ asset.id }}</code>
      <span class="asset-card-tags">
        <AssetTypeTag :family="assetFamily(asset, typesByName)" />
        <span v-if="regimeLabel(asset)" class="asset-type-tag measure">{{ regimeLabel(asset) }}</span>
        <span v-if="asset.disabledAt" class="env-status disabled">Disabled</span>
      </span>
      <span class="asset-card-type">{{ typeLabel(asset) }}</span>
      <TimestampInfo class="asset-card-time" :updated-at="asset.updatedAt" />
    </button>
    <button v-if="canAdd" class="asset-card asset-card-add" type="button" @click="emit('add')">
      <span class="asset-card-plus" aria-hidden="true">+</span>
      <span>Add an asset</span>
    </button>
    <p v-if="assets.length === 0 && !canAdd" class="table-empty">No assets in this environment.</p>
  </div>
</template>
