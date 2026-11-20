import type { ErrorObject } from 'ajv'

export interface AssetFormValidity {
  valid: boolean
  errors: ErrorObject[]
}
