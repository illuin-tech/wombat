import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createSimulation,
  createSimulationAsset,
  deleteSimulation,
  duplicateSimulation,
  evaluateSimulation,
  fetchSimulation,
  fetchSimulations,
  syncSimulationAsset,
  updateSimulationAsset,
} from '@/services/api/simulations'

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn().mockImplementation(async () => new Response(JSON.stringify({ status: { message: 'ok' }, payload: {} }), { status: 200 }))
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function call(index = 0): { url: string; method: string; body: unknown } {
  const [url, init] = fetchMock.mock.calls[index] as [string, RequestInit]
  return { url, method: init.method ?? 'GET', body: init.body ? JSON.parse(init.body as string) : undefined }
}

describe('simulations API', () => {
  it('reaches the sandbox of the simulation endpoints', async () => {
    await fetchSimulations()
    await fetchSimulation('what if')
    await createSimulation({ id: 'what-if', name: 'What if', description: null, sourceEnvironmentId: 'prod', assetIds: ['chatbot'] })
    await duplicateSimulation('what-if', { id: 'what-if-2', name: 'What if (copy)' })

    expect(call(0)).toMatchObject({ url: '/api/simulation/sandbox', method: 'GET' })
    expect(call(1)).toMatchObject({ url: '/api/simulation/sandbox/what%20if', method: 'GET' })
    expect(call(2)).toEqual({ url: '/api/simulation/sandbox', method: 'POST', body: { id: 'what-if', name: 'What if', description: null, sourceEnvironmentId: 'prod', assetIds: ['chatbot'] } })
    expect(call(3)).toMatchObject({ url: '/api/simulation/sandbox/what-if/duplicate', method: 'POST', body: { id: 'what-if-2', name: 'What if (copy)' } })
  })

  it('nests the assets under their simulation', async () => {
    fetchMock.mockImplementation(async () => new Response(null, { status: 204 }))
    await deleteSimulation('what-if')
    fetchMock.mockImplementation(async () => new Response(JSON.stringify({ status: { message: 'ok' }, payload: {} }), { status: 200 }))
    await createSimulationAsset('what-if', { sourceAssetId: 'chatbot' })
    await updateSimulationAsset('what-if', 'chatbot', { name: 'Copy', description: null, notes: 'Noted', properties: {} })
    await syncSimulationAsset('what-if', 'chatbot')

    expect(call(0)).toMatchObject({ url: '/api/simulation/sandbox/what-if', method: 'DELETE' })
    expect(call(1)).toMatchObject({ url: '/api/simulation/sandbox/what-if/assets', method: 'POST', body: { sourceAssetId: 'chatbot' } })
    expect(call(2)).toMatchObject({ url: '/api/simulation/sandbox/what-if/assets/chatbot', method: 'PUT' })
    expect(call(3)).toMatchObject({ url: '/api/simulation/sandbox/what-if/assets/chatbot/sync', method: 'POST' })
  })

  it('evaluates a simulation with the scopes asked for', async () => {
    await evaluateSimulation('what-if', { scopes: ['IMPACT'], referenceWindow: 'WEEK' })

    expect(call(0)).toEqual({ url: '/api/simulation/sandbox/what-if/evaluation', method: 'POST', body: { scopes: ['IMPACT'], referenceWindow: 'WEEK' } })
  })
})
