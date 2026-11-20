import { inlineDefinitions } from '@/components/asset-forms/model/schema'
import type { JsonSchema } from '@/domain/module'

type Schema = Record<string, unknown>

export interface OutlineValue {
  value: string
  title?: string
}

/** One alternative of a `oneOf` union: an object told apart by its discriminator's value. */
export interface OutlineVariant {
  title: string
  discriminator?: { name: string; value: string }
  description?: string
  properties: OutlineProperty[]
}

export interface OutlineProperty {
  name: string
  label: string
  /** e.g. `string`, `integer`, `list of objects`, `secret reference` */
  type: string
  required: boolean
  description?: string
  defaultValue?: string
  /** e.g. `≥ 0`, `not empty`, `at least 1 item`, `matches ^\d+$` */
  constraints: string[]
  /** The values an enum, or a `oneOf` of constants, allows */
  values: OutlineValue[]
  /** The properties of an object, or of the items of a list */
  properties: OutlineProperty[]
  /** The alternatives of a union, or of the items of a list */
  variants: OutlineVariant[]
}

const SECRET_KEY_FORMAT = 'secret-key'

/** The properties of an asset type's schema, described for reading rather than for a form. */
export function schemaOutline(schema: JsonSchema): OutlineProperty[] {
  return properties(inlineDefinitions(schema))
}

/** e.g. `Output tokens per request` for `outputTokensPerRequest` */
export function humanize(name: string): string {
  const words = name.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/[_-]/g, ' ').toLowerCase().trim()
  return words.charAt(0).toUpperCase() + words.slice(1)
}

function properties(node: Schema): OutlineProperty[] {
  const required = new Set((node.required ?? []) as string[])
  return Object.entries((node.properties ?? {}) as Record<string, Schema>)
    .map(([name, property]) => describe(name, property, required.has(name)))
}

function describe(name: string, node: Schema, required: boolean): OutlineProperty {
  const items = isSchema(node.items) ? node.items : null
  const shape = items ?? node
  return {
    name,
    label: typeof node.title === 'string' ? node.title : humanize(name),
    type: typeLabel(node, items),
    required,
    description: typeof node.description === 'string' ? node.description : undefined,
    defaultValue: defaultLabel(node.default),
    constraints: [...constraints(node), ...(items ? constraints(items) : [])],
    values: values(shape),
    properties: properties(shape),
    variants: variants(shape),
  }
}

function typeLabel(node: Schema, items: Schema | null): string {
  if (items)
    return `list of ${plural(typeLabel(items, isSchema(items.items) ? items.items : null))}`
  if (node.format === SECRET_KEY_FORMAT) return 'secret reference'
  if (variants(node).length > 0) return 'one of'
  const types = (Array.isArray(node.type) ? node.type : [node.type]).filter(type => typeof type === 'string' && type !== 'null') as string[]
  const type = types.join(' or ') || 'any'
  return typeof node.format === 'string' ? `${type} (${node.format})` : type
}

/** e.g. `strings (time)` for `string (time)`, `secret references` for `secret reference` */
function plural(type: string): string {
  if (type === 'one of') return 'alternatives'
  const [noun, format] = type.split(' (')
  return `${noun}s${format ? ` (${format}` : ''}`
}

function constraints(node: Schema): string[] {
  const found: string[] = []
  if (Array.isArray(node.type) && node.type.includes('null')) found.push('may be null')
  if (typeof node.minimum === 'number') found.push(`≥ ${node.minimum}`)
  if (typeof node.exclusiveMinimum === 'number') found.push(`> ${node.exclusiveMinimum}`)
  if (typeof node.maximum === 'number') found.push(`≤ ${node.maximum}`)
  if (typeof node.exclusiveMaximum === 'number') found.push(`< ${node.exclusiveMaximum}`)
  if (node.minLength === 1) found.push('not empty')
  else if (typeof node.minLength === 'number') found.push(`at least ${node.minLength} characters`)
  if (typeof node.maxLength === 'number') found.push(`at most ${node.maxLength} characters`)
  if (typeof node.minItems === 'number') found.push(`at least ${node.minItems} item${node.minItems === 1 ? '' : 's'}`)
  if (typeof node.maxItems === 'number') found.push(`at most ${node.maxItems} item${node.maxItems === 1 ? '' : 's'}`)
  /* A secret reference is told by its type: its pattern is the one of every secret key */
  if (typeof node.pattern === 'string' && node.format !== SECRET_KEY_FORMAT) found.push(`matches ${node.pattern}`)
  return found
}

function values(node: Schema): OutlineValue[] {
  if (Array.isArray(node.enum))
    return node.enum.map(value => ({ value: String(value) }))
  if (Array.isArray(node.oneOf) && node.oneOf.every(option => isSchema(option) && 'const' in option))
    return (node.oneOf as Schema[]).map(option => ({ value: String(option.const), title: typeof option.title === 'string' ? option.title : undefined }))
  return []
}

function variants(node: Schema): OutlineVariant[] {
  if (!Array.isArray(node.oneOf) || node.oneOf.some(option => !isSchema(option) || !isSchema(option.properties)))
    return []
  return (node.oneOf as Schema[]).map((option, index) => {
    const discriminator = Object.entries(option.properties as Record<string, Schema>).find(([, property]) => 'const' in property)
    return {
      title: typeof option.title === 'string' ? option.title : `Alternative ${index + 1}`,
      discriminator: discriminator && { name: discriminator[0], value: String(discriminator[1].const) },
      description: typeof option.description === 'string' ? option.description : undefined,
      properties: properties(option).filter(property => property.name !== discriminator?.[0]),
    }
  })
}

/* An object default, e.g. the first alternative of a union, says nothing a reader needs */
function defaultLabel(value: unknown): string | undefined {
  if (value === undefined || value === null || (typeof value === 'object' && !Array.isArray(value))) return undefined
  return typeof value === 'string' ? value : JSON.stringify(value)
}

function isSchema(value: unknown): value is Schema {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}
