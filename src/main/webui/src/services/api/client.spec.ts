import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, request } from '@/services/api/client'

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
  })

  it('has no reason without a JSON body', async () => {
    respond(400, '')
    expect((await failure()).reason).toBeNull()
  })
})
