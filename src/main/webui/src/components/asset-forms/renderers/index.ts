import { markRaw } from 'vue'
import type { JsonFormsRendererRegistryEntry } from '@jsonforms/core'
import { vanillaRenderers } from '@jsonforms/vue-vanilla'
import ArrayControl, { tester as arrayTester } from './ArrayControl.vue'
import FieldControl, { tester as fieldTester } from './FieldControl.vue'
import SecretKeyControl, { tester as secretKeyTester } from './SecretKeyControl.vue'
import UnionControl, { tester as unionTester } from './UnionControl.vue'

/*
 * The entries are built here from each SFC's default export: the build attaches the template to that export only, and
 * drops it when the export is unused, which leaves a component that renders nothing in production
 */
const entries: JsonFormsRendererRegistryEntry[] = [
  { renderer: ArrayControl, tester: arrayTester },
  { renderer: FieldControl, tester: fieldTester },
  { renderer: SecretKeyControl, tester: secretKeyTester },
  { renderer: UnionControl, tester: unionTester },
]

/* JSON Forms keeps the renderers in its reactive state: components must not become reactive themselves */
export const assetRenderers: JsonFormsRendererRegistryEntry[] = [...vanillaRenderers, ...entries]
  .map(entry => ({ ...entry, renderer: markRaw(entry.renderer) }))
