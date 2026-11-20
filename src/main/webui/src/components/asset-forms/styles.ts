import { defaultStyles, mergeStyles } from '@jsonforms/vue-vanilla'

/** The vanilla renderers' classes, plus the application's buttons; the rest is styled in asset-forms.css. */
export const assetFormStyles = mergeStyles(defaultStyles, {
  dialog: {
    buttonPrimary: 'btn-apply',
    buttonSecondary: 'btn-reset',
  },
})
