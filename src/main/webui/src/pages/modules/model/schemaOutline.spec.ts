import { describe, expect, it } from 'vitest'
import { humanize, schemaOutline, type OutlineProperty } from './schemaOutline'

/* Trimmed from the schemas the server serves for llm-anthropic, llm-static and kubernetes-api */
const schema = {
  $schema: 'https://json-schema.org/draft/2020-12/schema',
  type: 'object',
  required: ['adminKey', 'profile'],
  properties: {
    apiUrl: { type: 'string', description: 'Base URL of the Anthropic API', default: 'https://api.anthropic.com' },
    adminKey: { type: 'string', format: 'secret-key', pattern: '^[A-Za-z0-9_-]+$', description: 'Secret holding an Admin API key' },
    readTimeout: { type: ['string', 'null'], format: 'duration' },
    period: { type: 'string', pattern: '^\\d+\\s*[smhSMH]$' },
    profile: {
      type: 'object',
      required: ['models'],
      properties: {
        location: { type: 'string', default: 'USA', oneOf: [{ const: 'FRA', title: 'France' }, { const: 'USA', title: 'United States' }] },
        outputTokensPerRequest: { type: 'integer', default: 500, exclusiveMinimum: 0 },
        models: {
          type: 'array',
          minItems: 1,
          items: {
            type: 'object',
            required: ['provider'],
            properties: {
              provider: { type: 'string', enum: ['anthropic', 'mistralai'] },
              scenarios: {
                type: 'array',
                items: {
                  default: { type: 'constant' },
                  oneOf: [
                    {
                      type: 'object', title: 'Constant', description: 'Constant request volume',
                      properties: { requestPerYear: { type: 'integer', minimum: 0 }, applicability: { $ref: '#/$defs/ApplicabilityFilter' }, type: { const: 'constant', default: 'constant' } },
                    },
                    {
                      type: 'object', title: 'Batch activity',
                      properties: { triggerTime: { type: 'string', format: 'time' }, type: { const: 'batch_activity', default: 'batch_activity' } },
                    },
                  ],
                },
              },
            },
          },
        },
      },
    },
  },
  $defs: {
    ApplicabilityFilter: {
      type: 'object',
      description: 'Days and months a scenario applies to',
      properties: { daysOfWeek: { type: 'array', items: { type: 'string', enum: ['MONDAY', 'SUNDAY'] } } },
    },
  },
}

function find(properties: OutlineProperty[], name: string): OutlineProperty {
  const found = properties.find(property => property.name === name)
  if (!found) throw new Error(`No property ${name} in ${properties.map(property => property.name)}`)
  return found
}

const outline = schemaOutline(schema)

describe('schemaOutline', () => {
  it('lists the properties in the order of the schema, with labels and whether they are required', () => {
    expect(outline.map(property => property.name)).toEqual(['apiUrl', 'adminKey', 'readTimeout', 'period', 'profile'])
    expect(find(outline, 'apiUrl')).toMatchObject({ label: 'Api url', type: 'string', required: false, defaultValue: 'https://api.anthropic.com' })
    expect(find(outline, 'adminKey').required).toBe(true)
  })

  it('tells a secret reference by its type, without the pattern of every secret key', () => {
    expect(find(outline, 'adminKey')).toMatchObject({ type: 'secret reference', constraints: [] })
  })

  it('tells a nullable type, a format and a pattern', () => {
    expect(find(outline, 'readTimeout')).toMatchObject({ type: 'string (duration)', constraints: ['may be null'] })
    expect(find(outline, 'period').constraints).toEqual(['matches ^\\d+\\s*[smhSMH]$'])
  })

  it('lists the values of a oneOf of constants with their titles, and of an enum', () => {
    const profile = find(outline, 'profile')
    expect(find(profile.properties, 'location')).toMatchObject({
      defaultValue: 'USA',
      values: [{ value: 'FRA', title: 'France' }, { value: 'USA', title: 'United States' }],
      variants: [],
    })
    const provider = find(find(profile.properties, 'models').properties, 'provider')
    expect(provider.values).toEqual([{ value: 'anthropic' }, { value: 'mistralai' }])
  })

  it('tells bounds, and formats non-string defaults', () => {
    expect(find(find(outline, 'profile').properties, 'outputTokensPerRequest')).toMatchObject({ type: 'integer', defaultValue: '500', constraints: ['> 0'] })
  })

  it('describes a list by its items', () => {
    const models = find(find(outline, 'profile').properties, 'models')
    expect(models).toMatchObject({ type: 'list of objects', required: true, constraints: ['at least 1 item'] })
    expect(models.properties.map(property => property.name)).toEqual(['provider', 'scenarios'])
  })

  it('describes the alternatives of a union by title, with their discriminator apart', () => {
    const scenarios = find(find(find(outline, 'profile').properties, 'models').properties, 'scenarios')
    expect(scenarios.type).toBe('list of alternatives')
    expect(scenarios.defaultValue).toBeUndefined()
    expect(scenarios.variants.map(variant => [variant.title, variant.discriminator])).toEqual([
      ['Constant', { name: 'type', value: 'constant' }],
      ['Batch activity', { name: 'type', value: 'batch_activity' }],
    ])
    expect(scenarios.variants[0].properties.map(property => property.name)).toEqual(['requestPerYear', 'applicability'])
    expect(find(scenarios.variants[1].properties, 'triggerTime').type).toBe('string (time)')
  })

  it('resolves the definitions a property refers to', () => {
    const scenarios = find(find(find(outline, 'profile').properties, 'models').properties, 'scenarios')
    const applicability = find(scenarios.variants[0].properties, 'applicability')
    expect(applicability).toMatchObject({ type: 'object', description: 'Days and months a scenario applies to' })
    expect(find(applicability.properties, 'daysOfWeek')).toMatchObject({ type: 'list of strings', values: [{ value: 'MONDAY' }, { value: 'SUNDAY' }] })
  })
})

describe('humanize', () => {
  it('turns a camel-case or snake-case name into words', () => {
    expect(humanize('outputTokensPerRequest')).toBe('Output tokens per request')
    expect(humanize('batch_activity')).toBe('Batch activity')
  })
})
