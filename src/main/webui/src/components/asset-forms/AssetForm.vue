<script setup lang="ts">
import { computed, provide } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import type { JsonSchema } from '@jsonforms/core'
import type Ajv from 'ajv'
import type { ErrorObject } from 'ajv'
import type { AssetFormValidity } from './validity'
import { createAssetAjv } from './ajv'
import { assetRenderers } from './renderers'
import { assetFormStyles } from './styles'
import type { AssetProperties } from './model/properties'
import { inlineDefinitions } from './model/schema'
import './asset-forms.css'

/** The type-specific properties of an asset, as a form generated from its type's JSON schema. */
const props = withDefaults(defineProps<{
  schema: Record<string, unknown>
  readonly?: boolean
  /** Shows the validation errors, which a new asset only gets once its creation is attempted. */
  showErrors?: boolean
}>(), { readonly: false, showErrors: true })
const properties = defineModel<AssetProperties>({ required: true })
const emit = defineEmits<{ validity: [validity: AssetFormValidity] }>()

provide('styles', assetFormStyles)
const ajv = createAssetAjv() as unknown as Ajv
const config = { showUnfocusedDescription: true, restrict: true, trim: false }
const formSchema = computed(() => inlineDefinitions(props.schema) as JsonSchema)
const validationMode = computed(() => props.showErrors ? 'ValidateAndShow' : 'ValidateAndHide')

function onChange(event: { data: AssetProperties; errors?: ErrorObject[] }): void {
  properties.value = event.data
  const errors = event.errors ?? []
  emit('validity', { valid: errors.length === 0, errors })
}
</script>

<template>
  <div class="asset-form blur-reveal" :class="{ readonly }">
    <JsonForms
      :data="properties"
      :schema="formSchema"
      :renderers="assetRenderers"
      :ajv="ajv"
      :readonly="readonly"
      :config="config"
      :validation-mode="validationMode"
      @change="onChange"
    />
  </div>
</template>
