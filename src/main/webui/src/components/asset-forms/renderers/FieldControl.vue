<template>
  <control-wrapper
    v-bind="controlWrapper"
    :styles="styles"
    :is-focused="isFocused"
    :applied-options="appliedOptions"
  >
    <input
      :id="control.id + '-input'"
      ref="field"
      :type="kind === 'text' ? 'text' : kind"
      :step="step"
      :class="styles.control.input"
      :disabled="!control.enabled"
      :placeholder="appliedOptions.placeholder"
      @input="commit"
      @change="commit"
      @focus="isFocused = true"
      @blur="onBlur"
    />
  </control-wrapper>
</template>

<script lang="ts">
import {
  and,
  isDateControl,
  isDateTimeControl,
  isEnumControl,
  isIntegerControl,
  isMultiLineControl,
  isNumberControl,
  isOneOfEnumControl,
  isStringControl,
  isTimeControl,
  not,
  or,
  rankWith,
  type ControlElement,
  type JsonFormsRendererRegistryEntry,
} from '@jsonforms/core'
import { rendererProps, useJsonFormsControl, type RendererProps } from '@jsonforms/vue'
import { ControlWrapper, useVanillaControl } from '@jsonforms/vue-vanilla'
import { computed, defineComponent, onMounted, ref, watch } from 'vue'

type Kind = 'text' | 'number' | 'time'

/**
 * A text, number or time field, whose value the form gets as it is typed, rather than once the field is left: the form
 * knows at once that it changed, e.g. to offer saving it.
 * <p>
 * The field is not bound to the data: a bound field is rewritten with the data at each render, which loses what is
 * being typed whenever it reads differently from the data, e.g. 1.50 for 1.5, or a time Safari only reports once the
 * field is left. The data is written to the field only while it is not being typed in.
 */
const FieldControl = defineComponent({
  name: 'FieldControl',
  components: { ControlWrapper },
  props: { ...rendererProps<ControlElement>() },
  setup(props: RendererProps<ControlElement>) {
    const input = useVanillaControl(useJsonFormsControl(props))
    const field = ref<HTMLInputElement | null>(null)

    const kind = computed<Kind>(() => {
      const schema = input.control.value.schema
      const type = Array.isArray(schema.type) ? schema.type.find(candidate => candidate !== 'null') : schema.type
      if (type === 'number' || type === 'integer') return 'number'
      return schema.format === 'time' ? 'time' : 'text'
    })
    const integer = computed(() => {
      const type = input.control.value.schema.type
      return type === 'integer' || (Array.isArray(type) && type.includes('integer'))
    })
    const step = computed(() => {
      if (kind.value === 'time') return 60
      if (kind.value === 'number') return integer.value ? 1 : 'any'
      return undefined
    })

    /* What the field shows for the data: a time served as 08:00:00 reads 08:00 in a minute-step field */
    function format(data: unknown): string {
      if (data === undefined || data === null) return ''
      if (kind.value === 'time') return String(data).replace(/^(\d{2}:\d{2}):00(\.0+)?$/, '$1')
      return String(data)
    }

    /* An incomplete number, e.g. "-" or "1e", reads as an empty field: the data is then unset until it is complete */
    function parse(text: string): unknown {
      if (text === '') return undefined
      return kind.value === 'number' ? Number(text) : text
    }

    const shown = computed(() => format(input.control.value.data))

    function show(value: string): void {
      if (field.value && document.activeElement !== field.value && field.value.value !== value)
        field.value.value = value
    }

    function commit(): void {
      const text = field.value?.value ?? ''
      if (text !== shown.value)
        input.handleChange(input.control.value.path, parse(text))
    }

    function onBlur(): void {
      commit()
      input.isFocused.value = false
    }

    onMounted(() => show(shown.value))
    watch(shown, show)

    return { ...input, field, kind, step, commit, onBlur }
  },
})

export default FieldControl

const isPlainTextControl = and(
  isStringControl,
  not(or(isEnumControl, isOneOfEnumControl, isMultiLineControl, isDateControl, isDateTimeControl)),
)

export const entry: JsonFormsRendererRegistryEntry = {
  renderer: FieldControl,
  tester: rankWith(3, or(isTimeControl, isPlainTextControl, isNumberControl, isIntegerControl)),
}
</script>
