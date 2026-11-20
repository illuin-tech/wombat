import { markRaw } from 'vue'
import type { JsonFormsRendererRegistryEntry } from '@jsonforms/core'
import { vanillaRenderers } from '@jsonforms/vue-vanilla'
import { entry as arrayEntry } from './ArrayControl.vue'
import { entry as fieldEntry } from './FieldControl.vue'
import { entry as secretKeyEntry } from './SecretKeyControl.vue'

/* JSON Forms keeps the renderers in its reactive state: components must not become reactive themselves */
export const assetRenderers: JsonFormsRendererRegistryEntry[] = [...vanillaRenderers, arrayEntry, fieldEntry, secretKeyEntry]
  .map(entry => ({ ...entry, renderer: markRaw(entry.renderer) }))
