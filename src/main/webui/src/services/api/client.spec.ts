import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, request, requestResponse } from '@/services/api/client'

function respond(status: number, body: string): void {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(body, { status, headers: { 'Content-Type': 'application/json' } })))
}

async function failure(): Promise<ApiError> {
  try {
    await request('/api/impact', { method: 'POST', body: {} })
  } catch (caught) {
    return caught as ApiError
  }
  throw new Error('The request should have failed')
}

describe('request errors', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('keeps the reason the server gives', async () => {
    respond(400, JSON.stringify({ status: { message: 'No assets matched scope', metadata: { reason: 'no-data' } }, payload: null }))
    const error = await failure()
    expect(error.status).toBe(400)
    expect(error.reason).toBe('no-data')
    expect(error.serverMessage).toBe('No assets matched scope')
  })

  it('keeps the metadata the server gives', async () => {
    const failures = [{ assetIdentity: { id: 'cluster', environmentId: 'prod', type: 'tech.illuin.wombat-module.kubernetes-api' }, reason: 'Boom' }]
    respond(400, JSON.stringify({ status: { message: 'No assets matched scope', metadata: { reason: 'no-data', failures } }, payload: null }))
    const error = await failure()
    expect(error.metadata).toEqual({ reason: 'no-data', failures })
  })

  it('has no reason without a JSON body', async () => {
    respond(400, '')
    const error = await failure()
    expect(error.reason).toBeNull()
    expect(error.serverMessage).toBeNull()
    expect(error.metadata).toEqual({})
  })
})

describe('request envelope', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('resolves to the status along with the payload', async () => {
    respond(200, JSON.stringify({ status: { message: 'Success', metadata: { failures: [] } }, payload: [1] }))
    const response = await requestResponse<number[]>('/api/impact', { method: 'POST', body: {} })
    expect(response.payload).toEqual([1])
    expect(response.status.metadata).toEqual({ failures: [] })
  })
})
