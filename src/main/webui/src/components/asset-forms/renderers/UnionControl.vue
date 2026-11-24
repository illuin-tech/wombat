<template>
  <fieldset v-if="control.visible" class="group af-union">
    <legend class="group-label">
      {{ control.label }}<span v-if="control.required" class="asterisk">*</span>
    </legend>
    <div class="control">
      <label :for="`${control.id}-kind`" class="label">Kind</label>
      <select :id="`${control.id}-kind`" class="select" :value="variantIndex" :disabled="!control.enabled" @change="switchVariant">
        <option v-for="(option, index) in variants" :key="index" :value="index">{{ option.title ?? `Kind ${index + 1}` }}</option>
      </select>
      <div class="description">{{ control.description || variant.description }}</div>
    </div>
    <!-- Errors are left to the kind's own fields: the union's would list those of every other kind -->
    <dispatch-renderer
      :schema="variant"
      :uischema="uiSchema"
      :path="control.path"
      :enabled="control.enabled"
      :renderers="control.renderers"
      :cells="control.cells"
    />

    <ConfirmDialog
      :open="pendingVariant !== null"
      :title="`Change this ${control.label.toLowerCase()}?`"
      confirm-label="Change"
      @close="pendingVariant = null"
      @confirm="confirmSwitch"
    >
      Its kind becomes "{{ pendingVariant?.title }}", and the settings that kind does not have are reset.
    </ConfirmDialog>
  </fieldset>
</template>

<script lang="ts">
import {
  Actions,
  and,
  Generate,
  isOneOfControl,
  rankWith,
  schemaMatches,
  type ControlElement,
  type CoreActions,
  type Dispatch,
  type JsonSchema,
  type RankedTester,
  type UISchemaElement,
} from '@jsonforms/core'
import { DispatchRenderer, rendererProps, useJsonFormsControl, type RendererProps } from '@jsonforms/vue'
import { computed, defineComponent, inject, ref } from 'vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { defaultProperties, discriminatorOf } from '../model/schema'

type Item = Record<string, unknown>

/** A property holding one of several kinds, e.g. a cloud or a bare server profile: a field for its kind, then the kind's own fields. */
const UnionControl = defineComponent({
  name: 'UnionControl',
  components: { ConfirmDialog, DispatchRenderer },
  props: { ...rendererProps<ControlElement>() },
  setup(props: RendererProps<ControlElement>) {
    const input = useJsonFormsControl(props)
    const dispatch = inject<Dispatch<CoreActions>>('dispatch')!
    const control = input.control
    /* A switch of kind waiting for its confirmation */
    const pendingVariant = ref<JsonSchema | null>(null)

    const variants = computed(() => control.value.schema.oneOf!)
    const discriminator = computed(() => discriminatorOf(variants.value)!)
    const value = computed(() => (control.value.data ?? {}) as Item)
    /* A value without its kind is of the first one, as the server reads it */
    const variantIndex = computed(() => Math.max(0, variants.value.findIndex(variant => variant.properties![discriminator.value].const === value.value[discriminator.value])))
    const variant = computed(() => variants.value[variantIndex.value])

    /* Generated once per schema: a new layout at each render would rebuild the fields */
    const layouts = new WeakMap<JsonSchema, UISchemaElement>()
    const uiSchema = computed(() => {
      let layout = layouts.get(variant.value)
      if (!layout) {
        layout = Generate.uiSchema(variant.value, 'VerticalLayout', '#', control.value.rootSchema)
        layouts.set(variant.value, layout)
      }
      return layout
    })

    function filled(entry: unknown): boolean {
      return entry !== undefined && entry !== null && entry !== ''
    }

    /* Switching kinds keeps the settings both share, e.g. a location: only confirmed when it drops others */
    function switchVariant(event: Event): void {
      const select = event.target as HTMLSelectElement
      const next = variants.value[Number(select.value)]
      const dropped = Object.entries(value.value).some(([name, entry]) => name !== discriminator.value && !(name in (next.properties ?? {})) && filled(entry))
      if (dropped) {
        /* The value keeps its kind until the switch is confirmed */
        select.value = String(variantIndex.value)
        pendingVariant.value = next
        return
      }
      applySwitch(next)
    }

    function applySwitch(next: JsonSchema): void {
      const kept = Object.entries(value.value).filter(([name]) => name !== discriminator.value && name in (next.properties ?? {}))
      dispatch(Actions.update(control.value.path, () => ({ ...defaultProperties(next as Item), ...Object.fromEntries(kept) })))
    }

    function confirmSwitch(): void {
      if (pendingVariant.value) applySwitch(pendingVariant.value)
      pendingVariant.value = null
    }

    return { ...input, pendingVariant, variants, variantIndex, variant, uiSchema, switchVariant, confirmSwitch }
  },
})

export default UnionControl

/* Above the vanilla oneOf renderer, which neither writes the kind of an empty value nor keeps what kinds share */
export const tester: RankedTester = rankWith(6, and(isOneOfControl, schemaMatches(schema => discriminatorOf(schema.oneOf ?? []) !== null)))
</script>
