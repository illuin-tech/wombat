<template>
  <control-wrapper
    v-bind="controlWrapper"
    :styles="styles"
    :is-focused="isFocused"
    :applied-options="appliedOptions"
  >
    <div class="af-secret">
      <input
        :id="control.id + '-input'"
        :class="styles.control.input"
        :value="control.data"
        :disabled="!control.enabled"
        autocomplete="off"
        spellcheck="false"
        placeholder="SECRET_NAME"
        @input="onInput"
        @focus="isFocused = true"
        @blur="isFocused = false"
      />
      <span v-if="status !== 'idle'" class="af-secret-status" :class="status" :title="statusText" role="img" :aria-label="statusText">
        <span v-if="status === 'checking'" class="af-spinner" />
        <svg v-else-if="status === 'found'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6 9 17l-5-5" /></svg>
        <svg v-else-if="status === 'invalid'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="m15 9-6 6M9 9l6 6" /></svg>
        <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z" /><path d="M12 9v4M12 17h.01" /></svg>
      </span>
    </div>
  </control-wrapper>
</template>

<script lang="ts">
import { and, formatIs, isStringControl, rankWith, type ControlElement, type RankedTester } from '@jsonforms/core'
import { rendererProps, useJsonFormsControl, type RendererProps } from '@jsonforms/vue'
import { ControlWrapper, useVanillaControl } from '@jsonforms/vue-vanilla'
import { useDebounceFn } from '@vueuse/core'
import { computed, defineComponent, inject, ref, watch } from 'vue'
import { SECRET_KEY_FORMAT } from '../ajv'
import { SECRET_CHECKER_KEY } from '../secretChecker'

type SecretStatus = 'idle' | 'invalid' | 'checking' | 'found' | 'missing' | 'unknown'

/**
 * The name of a secret: secrets cannot be listed, but the server tells whether a name resolves.
 * Creating a secret from here is left for when the server has a secret writer.
 */
const SecretKeyControl = defineComponent({
  name: 'SecretKeyControl',
  components: { ControlWrapper },
  props: { ...rendererProps<ControlElement>() },
  setup(props: RendererProps<ControlElement>) {
    const input = useVanillaControl(useJsonFormsControl(props), target => target.value || undefined)
    const checker = inject(SECRET_CHECKER_KEY, null)
    const status = ref<SecretStatus>('idle')
    const pattern = computed(() => {
      const source = input.control.value.schema.pattern
      return source ? new RegExp(source) : null
    })
    let latest = 0

    const check = useDebounceFn(async (key: string, call: number) => {
      try {
        const exists = await checker!(key)
        if (call === latest) status.value = exists ? 'found' : 'missing'
      } catch {
        if (call === latest) status.value = 'unknown'
      }
    }, 400)

    watch(() => input.control.value.data as string | undefined, key => {
      const call = ++latest
      if (!key) status.value = 'idle'
      else if (pattern.value && !pattern.value.test(key)) status.value = 'invalid'
      else if (!checker) status.value = 'idle'
      else {
        status.value = 'checking'
        void check(key, call)
      }
    }, { immediate: true })

    const statusText = computed(() => {
      const key = input.control.value.data as string | undefined
      switch (status.value) {
        case 'invalid': return 'Letters, digits, dashes and underscores only'
        case 'checking': return 'Checking…'
        case 'found': return 'Known secret'
        case 'missing': return `No secret named ${key} is known: declare it before saving`
        case 'unknown': return 'The secret could not be checked'
        default: return ''
      }
    })

    function onInput(event: Event): void {
      input.handleChange(input.control.value.path, (event.target as HTMLInputElement).value || undefined)
    }

    return { ...input, status, statusText, onInput }
  },
})

export default SecretKeyControl

export const tester: RankedTester = rankWith(5, and(isStringControl, formatIs(SECRET_KEY_FORMAT)))
</script>
