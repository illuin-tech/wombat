import { describe, expect, it } from 'vitest'
import { SIMULATION_ASSET_RECORD_FIELDS } from '@/domain/simulation'
import { isSameProperties, toAssetProperties } from './properties'

describe('asset properties', () => {
  it('keeps only the type-specific properties, without nulls', () => {
    expect(toAssetProperties({
      id: 'my-llm', environmentId: 'preprod', name: 'My LLM', type: 'tech.illuin.wombat-module.llm-anthropic',
      uuid: 'u', owner: null, description: 'd', provisioned: false, createdAt: 'c', updatedAt: 'u', disabledAt: null,
      adminKey: 'KEY', proxyUrl: null, profile: { location: 'USA', models: [], extra: null },
    })).toEqual({ adminKey: 'KEY', profile: { location: 'USA', models: [] } })
  })

  it('leaves out the fields that whatever holds the asset adds to its record', () => {
    const copy = { id: 'chatbot', type: 't', simulationId: 'what-if', notes: 'n', source: { assetId: 'chatbot' }, profile: { zoneId: 'UTC' } }

    expect(toAssetProperties(copy, SIMULATION_ASSET_RECORD_FIELDS)).toEqual({ profile: { zoneId: 'UTC' } })
  })

  it('compares properties deeply, a null property being an absent one', () => {
    expect(isSameProperties({ a: 1, b: { c: [1, { d: 2 }] } }, { b: { c: [1, { d: 2 }] }, a: 1, e: null })).toBe(true)
    expect(isSameProperties({ a: 1, b: { c: [1, 2] } }, { a: 1, b: { c: [2, 1] } })).toBe(false)
    expect(isSameProperties({ a: 1 }, { a: 1, b: 0 })).toBe(false)
  })
})
