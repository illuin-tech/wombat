import { describe, expect, it } from 'vitest'
import { encodeServices, parseServices } from '@/pages/impact/model/servicesQuery'

describe('services query (UIController.parseServices)', () => {
  it('parses asset ids with their services', () => {
    const parsed = parseServices(['test-cluster=api, worker', 'test-llm', ' =ignored', '', 'other='])
    expect([...parsed.entries()]).toEqual([
      ['test-cluster', ['api', 'worker']],
      ['test-llm', []],
      ['other', []],
    ])
  })

  it('round-trips through the encoder', () => {
    const selection = new Map([['a', ['x', 'y']], ['b', []]])
    expect(encodeServices(selection)).toEqual(['a=x,y', 'b'])
    expect(parseServices(encodeServices(selection))).toEqual(selection)
  })
})
