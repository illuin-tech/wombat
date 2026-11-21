<template>
  <fieldset v-if="control.visible" class="af-array" :class="mode">
    <legend class="af-array-label">
      {{ control.label }}<span v-if="control.required" class="asterisk">*</span>
    </legend>
    <p v-if="description" class="description">{{ description }}</p>

    <!-- A set of options, e.g. days of the week: each one toggles -->
    <div v-if="mode === 'options'" class="af-pills" role="group" :aria-label="control.label">
      <button
        v-for="option in options"
        :key="option"
        type="button"
        class="af-pill"
        :class="{ selected: items.includes(option) }"
        :aria-pressed="items.includes(option)"
        :disabled="!control.enabled"
        @click="toggle(option)"
      >{{ humanize(option) }}</button>
    </div>

    <!-- A list of plain values, e.g. identifiers: one field each -->
    <template v-else-if="mode === 'values'">
      <div v-for="(item, index) in items" :key="`${control.path}-${index}`" class="af-value-row">
        <input
          ref="valueInputs"
          class="input"
          :type="valueType === 'string' ? 'text' : 'number'"
          :value="item"
          :disabled="!control.enabled"
          :aria-label="`${capitalize(singular)} ${index + 1}`"
          @change="setValue(index, $event)"
        />
        <button v-if="control.enabled" type="button" class="af-icon-button" :aria-label="`Remove ${singular} ${index + 1}`" title="Remove" @click="remove(index)">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12" /></svg>
        </button>
      </div>
      <button v-if="control.enabled" type="button" class="af-array-add-link" @click="add">+ Add {{ singular }}</button>
    </template>

    <!-- A list of objects, e.g. scenarios: a card each, with its kind when there are several -->
    <template v-else>
      <div v-for="(item, index) in items" :key="`${control.path}-${index}`" class="af-card">
        <div class="af-card-head">
          <span class="af-card-title">{{ capitalize(singular) }} {{ index + 1 }}</span>
          <select
            v-if="variants"
            class="select af-card-variant"
            :value="variantIndex(item)"
            :disabled="!control.enabled"
            :aria-label="`Kind of ${singular} ${index + 1}`"
            @change="switchVariant(index, $event)"
          >
            <option v-for="(variant, variantIndexValue) in variants" :key="variantIndexValue" :value="variantIndexValue">{{ variant.title ?? `Kind ${variantIndexValue + 1}` }}</option>
          </select>
          <span v-if="control.enabled" class="af-card-actions">
            <button type="button" class="af-icon-button" :disabled="index === 0" :aria-label="`Move ${singular} ${index + 1} up`" title="Move up" @click="move(index, -1)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m18 15-6-6-6 6" /></svg>
            </button>
            <button type="button" class="af-icon-button" :disabled="index === items.length - 1" :aria-label="`Move ${singular} ${index + 1} down`" title="Move down" @click="move(index, 1)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
            </button>
            <button type="button" class="af-icon-button danger" :aria-label="`Remove ${singular} ${index + 1}`" title="Remove" @click="remove(index)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6" /></svg>
            </button>
          </span>
        </div>
        <p v-if="variants && variants[variantIndex(item)]?.description" class="description">{{ variants[variantIndex(item)].description }}</p>
        <dispatch-renderer
          :schema="itemSchema(item)"
          :uischema="itemUiSchema(item)"
          :path="composePaths(control.path, `${index}`)"
          :enabled="control.enabled"
          :renderers="control.renderers"
          :cells="control.cells"
        />
      </div>
      <button v-if="control.enabled" type="button" class="af-array-add" @click="add">+ Add {{ singular }}</button>
    </template>

    <p v-if="mode !== 'options' && items.length === 0 && !control.enabled" class="af-array-empty">None.</p>
    <div v-if="control.errors" class="error">{{ control.errors }}</div>

    <ConfirmDialog
      :open="pendingSwitch !== null"
      :title="`Change this ${singular}?`"
      confirm-label="Change"
      @close="pendingSwitch = null"
      @confirm="confirmSwitch"
    >
      Its kind becomes "{{ pendingSwitch?.variant.title }}", and its settings are reset.
    </ConfirmDialog>
  </fieldset>
</template>

<script lang="ts">
import {
  Actions,
  composePaths,
  Generate,
  Resolve,
  rankWith,
  schemaTypeIs,
  type ControlElement,
  type CoreActions,
  type Dispatch,
  type JsonSchema,
  type RankedTester,
  type UISchemaElement,
} from '@jsonforms/core'
import { DispatchRenderer, rendererProps, useJsonFormsArrayControl, type RendererProps } from '@jsonforms/vue'
import { computed, defineComponent, inject, nextTick, ref } from 'vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { defaultProperties } from '../model/schema'

type Item = Record<string, unknown>

/** e.g. "scenario" for "Scenarios", "workspace id" for "Workspace Ids" */
function singularOf(label: string): string {
  const lower = label.trim().toLowerCase()
  if (lower.endsWith('ies')) return `${lower.slice(0, -3)}y`
  if (lower.endsWith('s')) return lower.slice(0, -1)
  return lower || 'item'
}

/** The property holding a distinct `const` in every variant, e.g. a scenario's `type`. */
function discriminatorOf(variants: JsonSchema[]): string | null {
  const first = variants[0]?.properties ?? {}
  return Object.keys(first).find(name => variants.every(variant => variant.properties?.[name]?.const !== undefined)) ?? null
}

/** Arrays as lists anyone can read at a glance: toggles for options, fields for values, cards for objects. */
const ArrayControl = defineComponent({
  name: 'ArrayControl',
  components: { ConfirmDialog, DispatchRenderer },
  props: { ...rendererProps<ControlElement>() },
  setup(props: RendererProps<ControlElement>) {
    const input = useJsonFormsArrayControl(props)
    /* The array composition only adds, removes and moves items: other changes replace the array, as controls do */
    const dispatch = inject<Dispatch<CoreActions>>('dispatch')!
    const control = input.control
    const valueInputs = ref<HTMLInputElement[]>([])
    /* A switch of kind waiting for its confirmation */
    const pendingSwitch = ref<{ index: number; variant: JsonSchema } | null>(null)

    const items = computed(() => (control.value.data ?? []) as unknown[])
    /* The schema of the items; the array's own, e.g. its description, is resolved from the parent's */
    const arraySchema = computed(() => Resolve.schema(props.schema, control.value.uischema.scope, control.value.rootSchema))
    const description = computed(() => arraySchema.value?.description ?? control.value.description)
    const singular = computed(() => singularOf(control.value.label))
    const options = computed(() => (control.value.schema.enum ?? []) as string[])
    const valueType = computed(() => {
      const type = control.value.schema.type
      return Array.isArray(type) ? type.find(candidate => candidate !== 'null') : type
    })
    const variants = computed(() => {
      const oneOf = control.value.schema.oneOf
      return oneOf && discriminatorOf(oneOf) ? oneOf : null
    })
    const discriminator = computed(() => variants.value ? discriminatorOf(variants.value) : null)
    const mode = computed(() => {
      if (options.value.length > 0) return 'options'
      if (valueType.value === 'string' || valueType.value === 'number' || valueType.value === 'integer') return 'values'
      return 'cards'
    })

    function variantIndex(item: unknown): number {
      const value = (item as Item | undefined)?.[discriminator.value!]
      return Math.max(0, variants.value!.findIndex(variant => variant.properties![discriminator.value!].const === value))
    }

    function itemSchema(item: unknown): JsonSchema {
      return variants.value ? variants.value[variantIndex(item)] : control.value.schema
    }

    /* Generated once per schema: a new layout at each render would rebuild the card's fields */
    const layouts = new WeakMap<JsonSchema, UISchemaElement>()
    function itemUiSchema(item: unknown): UISchemaElement {
      const schema = itemSchema(item)
      let layout = layouts.get(schema)
      if (!layout) {
        layout = Generate.uiSchema(schema, 'VerticalLayout', '#', control.value.rootSchema)
        layouts.set(schema, layout)
      }
      return layout
    }

    function update(next: unknown[]): void {
      dispatch(Actions.update(control.value.path, () => next))
    }

    function toggle(option: string): void {
      const selected = new Set(items.value)
      if (selected.has(option)) selected.delete(option)
      else selected.add(option)
      update(options.value.filter(candidate => selected.has(candidate)))
    }

    function setValue(index: number, event: Event): void {
      const raw = (event.target as HTMLInputElement).value
      const next = [...items.value]
      next[index] = valueType.value === 'string' ? raw : raw === '' ? undefined : Number(raw)
      update(next)
    }

    async function add(): Promise<void> {
      if (mode.value === 'values') {
        update([...items.value, valueType.value === 'string' ? '' : undefined])
        await nextTick()
        valueInputs.value.at(-1)?.focus()
        return
      }
      update([...items.value, defaultProperties(control.value.schema as Item)])
    }

    function remove(index: number): void {
      update(items.value.filter((_, candidate) => candidate !== index))
    }

    function move(index: number, offset: number): void {
      const next = [...items.value]
      const [moved] = next.splice(index, 1)
      next.splice(index + offset, 0, moved)
      update(next)
    }

    /* Switching the kind of an item resets it: only confirmed when it holds more than its kind */
    function switchVariant(index: number, event: Event): void {
      const select = event.target as HTMLSelectElement
      const item = (items.value[index] ?? {}) as Item
      const variant = variants.value![Number(select.value)]
      const filled = Object.entries(item).some(([name, value]) => name !== discriminator.value && value !== undefined && value !== null && value !== '')
      if (filled) {
        /* The item keeps its kind until the switch is confirmed */
        select.value = String(variantIndex(item))
        pendingSwitch.value = { index, variant }
        return
      }
      applySwitch(index, variant)
    }

    function applySwitch(index: number, variant: JsonSchema): void {
      const next = [...items.value]
      next[index] = defaultProperties(variant as Item)
      update(next)
    }

    function confirmSwitch(): void {
      if (pendingSwitch.value) applySwitch(pendingSwitch.value.index, pendingSwitch.value.variant)
      pendingSwitch.value = null
    }

    function humanize(value: string): string {
      const words = value.replace(/_/g, ' ').toLowerCase()
      return words.charAt(0).toUpperCase() + words.slice(1)
    }

    function capitalize(value: string): string {
      return value.charAt(0).toUpperCase() + value.slice(1)
    }

    return {
      ...input, valueInputs, pendingSwitch, items, description, singular, options, valueType, variants, mode,
      variantIndex, itemSchema, itemUiSchema, toggle, setValue, add, remove, move, switchVariant, confirmSwitch, humanize, capitalize, composePaths,
    }
  },
})

export default ArrayControl

export const tester: RankedTester = rankWith(6, schemaTypeIs('array'))
</script>
