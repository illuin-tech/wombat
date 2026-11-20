import { describe, expect, it } from 'vitest'
import { defaultProperties, inlineDefinitions } from './schema'

describe('schema definitions', () => {
  it('inlines local references, keeping the referencing description', () => {
    expect(inlineDefinitions({
      type: 'object',
      properties: { applicability: { $ref: '#/$defs/Filter', description: 'When it applies' } },
      $defs: { Filter: { type: 'object', description: 'A filter', properties: { days: { type: 'array' } } } },
    })).toEqual({
      type: 'object',
      properties: { applicability: { type: 'object', description: 'When it applies', properties: { days: { type: 'array' } } } },
    })
  })

  it('keeps recursive references and their definitions', () => {
    const schema = {
      $ref: '#/$defs/Node',
      $defs: { Node: { type: 'object', properties: { child: { $ref: '#/$defs/Node' } } } },
    }
    expect(inlineDefinitions(schema)).toEqual({
      type: 'object',
      properties: { child: { $ref: '#/$defs/Node' } },
      $defs: schema.$defs,
    })
  })
})

describe('default properties', () => {
  it('fills in the defaults, within the required objects only', () => {
    expect(defaultProperties({
      type: 'object',
      properties: {
        adminKey: { type: 'string', format: 'secret-key' },
        apiUrl: { type: 'string', default: 'https://api.anthropic.com' },
        profile: { $ref: '#/$defs/Profile' },
      },
      required: ['adminKey', 'profile'],
      $defs: {
        Profile: {
          type: 'object',
          properties: { location: { type: 'string', default: 'USA' }, outputTokensPerRequest: { type: 'integer', default: 500 }, models: { type: 'array' } },
        },
      },
    })).toEqual({
      apiUrl: 'https://api.anthropic.com',
      profile: { location: 'USA', outputTokensPerRequest: 500 },
    })
    expect(defaultProperties({
      type: 'object',
      properties: { optional: { type: 'object', properties: { a: { type: 'number', default: 1 } } } },
    })).toEqual({})
  })
})
