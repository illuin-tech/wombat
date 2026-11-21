import { describe, expect, it } from 'vitest'
import type { AssetInfo } from '@/domain/environment'
import type { ModuleMetadata } from '@/domain/module'
import { copyCandidates } from './copyCandidates'

const module = (localName: string, extra: Partial<ModuleMetadata> = {}): ModuleMetadata => ({
  type: { namespace: 'n', localName, name: `n.${localName}`, regime: 'MODELED', family: 'LLM' },
  assetClass: 'Asset', enabled: true, creatable: true, ...extra,
})
const modules = [module('llm-static'), module('llm-vertexai', { enabled: false }), module('llm-simulated', { creatable: false })]
const asset = (id: string, localName: string, extra: Partial<AssetInfo> = {}): AssetInfo => ({ id, name: id, type: `n.${localName}`, ...extra })

describe('copyCandidates', () => {
  it('picks the active assets whose module can run them, and tells why the others cannot be copied', () => {
    const candidates = copyCandidates([
      asset('d-static', 'llm-static'),
      asset('a-inactive', 'llm-static', { disabledAt: '2026-11-01T00:00:00Z' }),
      asset('c-vertex', 'llm-vertexai'),
      asset('b-simulated', 'llm-simulated'),
      asset('e-gone', 'gone'),
    ], modules)

    expect(candidates.map(candidate => [candidate.asset.id, candidate.blocked, candidate.pickedByDefault])).toEqual([
      ['a-inactive', null, false],
      ['b-simulated', 'Not for simulations', false],
      ['c-vertex', 'Module disabled', false],
      ['d-static', null, true],
      ['e-gone', 'Unknown type', false],
    ])
  })
})
