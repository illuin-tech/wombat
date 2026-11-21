<script setup lang="ts" generic="T extends AssetInfo">
import { assetFamily, assetTypeName, displayName, type ActivityRegime, type AssetInfo, type AssetType } from '@/domain/environment'
import AssetTypeTag from '@/components/AssetTypeTag.vue'
import LockBadge from '@/components/LockBadge.vue'
import RegimePill from '@/components/RegimePill.vue'
import TimestampInfo from '@/components/TimestampInfo.vue'

/** The assets of an environment or a simulation as cards; the `badges` slot adds to each one's tags. */
const props = withDefaults(defineProps<{
  assets: T[]
  typesByName: ReadonlyMap<string, AssetType>
  /** Whether the environment is provisioned, which locks all its assets. */
  locked?: boolean
  canAdd: boolean
  emptyText?: string
}>(), {
  locked: false,
  emptyText: 'No assets.',
})
const emit = defineEmits<{ open: [asset: T]; add: [] }>()
defineSlots<{ badges?: (props: { asset: T }) => unknown }>()

function typeLabel(asset: AssetInfo): string {
  const name = assetTypeName(asset)
  return (name && props.typesByName.get(name)?.localName) ?? name ?? 'Unknown type'
}

/** Left out when the regime is not known */
function knownRegime(asset: AssetInfo): ActivityRegime | null {
  const name = assetTypeName(asset)
  const regime = name ? props.typesByName.get(name)?.regime : undefined
  return regime && regime !== 'UNKNOWN' ? regime : null
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
        <RegimePill v-if="knownRegime(asset)" :regime="knownRegime(asset)!" />
        <span v-if="asset.disabledAt" class="status-chip">Disabled</span>
        <slot name="badges" :asset="asset" />
      </span>
      <span class="asset-card-type">{{ typeLabel(asset) }}</span>
      <TimestampInfo class="asset-card-time" :updated-at="asset.updatedAt" />
    </button>
    <button v-if="canAdd" class="asset-card asset-card-add" type="button" @click="emit('add')">
      <span class="asset-card-plus" aria-hidden="true">+</span>
      <span>Add an asset</span>
    </button>
    <p v-if="assets.length === 0 && !canAdd" class="table-empty">{{ emptyText }}</p>
  </div>
</template>
