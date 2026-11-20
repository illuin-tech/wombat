import Ajv2020 from 'ajv/dist/2020'
import addFormats from 'ajv-formats'

/** Format of the string properties naming a secret, rather than holding it. */
export const SECRET_KEY_FORMAT = 'secret-key'

/** Asset schemas are generated as JSON schema draft 2020-12, which the AJV instance JSON Forms creates does not read. */
export function createAssetAjv(): Ajv2020 {
  const ajv = new Ajv2020({ allErrors: true, verbose: true, strict: false })
  addFormats(ajv)
  // A Java LocalTime has no time zone, which the standard format requires: 08:00 and 08:00:00 are both accepted
  ajv.addFormat('time', /^\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/)
  // Its `pattern` carries the naming rule
  ajv.addFormat(SECRET_KEY_FORMAT, true)
  return ajv
}
