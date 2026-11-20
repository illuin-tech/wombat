import { defineAsyncComponent } from 'vue'
import AssetFormPlaceholder from './AssetFormPlaceholder.vue'

/* The form generator weighs more than the rest of the application: it is only loaded once a form is shown */
export const AssetForm = defineAsyncComponent({
  loader: () => import('./AssetForm.vue'),
  loadingComponent: AssetFormPlaceholder,
  delay: 0,
})
export { AssetFormPlaceholder }
export type { AssetFormValidity } from './validity'
export { SECRET_CHECKER_KEY, type SecretChecker } from './secretChecker'
export { isSameProperties, toAssetProperties, type AssetProperties } from './model/properties'
export { defaultProperties } from './model/schema'
