<script setup lang="ts">
import { computed } from 'vue'
import { FAMILY_HINTS } from '@/domain/assetTypeDisplay'
import type { FeaturePrecedence, ModuleInfo, ModulePrecedence } from '@/domain/module'
import AssetTypeTag from '@/components/AssetTypeTag.vue'
import PillHint from '@/components/PillHint.vue'
import { FEATURE_HINTS, FEATURE_LABELS } from '@/pages/modules/model/moduleList'

/**
 * Which family module serves first, for each family and component: as deployed, and after the redeploy if it changes.
 * Hovering the one serving first shows the whole chain a component goes through.
 */
const props = defineProps<{ precedence: ModulePrecedence; modules: readonly ModuleInfo[] }>()

type Side = 'deployed' | 'next'

const SIDE_TITLES: Record<Side, string> = { deployed: 'Deployed', next: 'After the redeploy' }

const sides = computed<Side[]>(() => props.precedence.changed ? ['deployed', 'next'] : ['deployed'])
const names = computed(() => new Map(props.modules.map(module => [module.type.name, module.type.localName])))

function label(name: string): string {
  return names.value.get(name) ?? name
}

function providers(feature: FeaturePrecedence, side: Side): string[] {
  return feature[side].map(label)
}
</script>

<template>
  <section class="precedence" aria-labelledby="precedenceTitle">
    <h2 id="precedenceTitle" class="precedence-title">Family component precedence</h2>
    <p class="form-hint">
      For each family, default behaviour is determined by the order in which family-level modules are registered. You can
      reorder the modules below to change their relative priority.
    </p>
    <div class="precedence-panels" :class="{ changed: precedence.changed }">
      <template v-for="(side, index) in sides" :key="side">
        <span v-if="index > 0" class="precedence-arrow" aria-hidden="true">→</span>
        <div class="precedence-panel" :class="side">
          <h3 v-if="precedence.changed" class="precedence-panel-title">{{ SIDE_TITLES[side] }}</h3>
          <div v-for="family in precedence.families" :key="family.family" class="precedence-family">
            <PillHint :hint="FAMILY_HINTS[family.family]"><AssetTypeTag :family="family.family" /></PillHint>
            <ul class="precedence-rows">
              <li
                v-for="feature in family.features"
                :key="feature.feature"
                class="precedence-row"
                :class="{ changed: precedence.changed && feature.changed }"
              >
                <PillHint :hint="FEATURE_HINTS[feature.feature]">
                  <span class="module-feature" :class="feature.feature">{{ FEATURE_LABELS[feature.feature] }}</span>
                </PillHint>
                <span v-if="providers(feature, side).length > 0">
                  <PillHint :width="480">
                    <strong class="precedence-first">{{ providers(feature, side)[0] }}</strong>
                    <template #hint>
                      <span class="precedence-chain">
                        <span class="precedence-stage own">Asset's own module</span>
                        <template v-for="name in providers(feature, side)" :key="name">
                          <span class="precedence-chain-arrow" aria-hidden="true">→</span>
                          <span class="visually-hidden">, then</span>
                          <span class="precedence-stage">{{ name }}</span>
                        </template>
                      </span>
                    </template>
                  </PillHint>
                  <span v-if="providers(feature, side).length > 1" class="precedence-then">, then {{ providers(feature, side).slice(1).join(', ') }}</span>
                </span>
                <span v-else class="precedence-then">None</span>
              </li>
            </ul>
          </div>
        </div>
      </template>
    </div>
  </section>
</template>
