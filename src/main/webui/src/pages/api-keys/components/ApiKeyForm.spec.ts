import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { ApiKeyInfo, ApiKeySettings } from '@/domain/access'
import { createApiKey } from '@/services/api/apiKeys'
import ApiKeyForm from './ApiKeyForm.vue'

vi.mock('@/services/api/apiKeys', () => ({ createApiKey: vi.fn() }))

const createApiKeyMock = vi.mocked(createApiKey)
const settings: ApiKeySettings = {
  grantablePermissions: ['api.impact.read'],
  maxLifetimeDays: 60,
  allowNeverExpiring: true,
}
const createdKey: ApiKeyInfo = {
  id: 'created',
  name: 'nightly build',
  permissions: ['api.impact.read'],
  owner: 'Alice',
  createdAt: '2026-01-10T12:00:00Z',
  expiresAt: '2026-02-14T00:00:00Z',
  revokedAt: null,
  lastUsedAt: null,
  token: 'secret-token',
}

afterEach(() => {
  vi.useRealTimers()
  vi.clearAllMocks()
})

describe('ApiKeyForm', () => {
  it('requires at least one permission before creating a key', async () => {
    const wrapper = mount(ApiKeyForm, { props: { settings } })

    await wrapper.get('.apikey-check input').setValue(false)
    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('[role="alert"]').text()).toBe('Pick at least one permission.')
    expect(createApiKeyMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('submits the trimmed name and expiry and emits the created key', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-01-10T12:00:00Z'))
    createApiKeyMock.mockResolvedValue(createdKey)
    const wrapper = mount(ApiKeyForm, { props: { settings } })

    await wrapper.get('input[type="text"]').setValue('  nightly build  ')
    await wrapper.get('input[type="date"]').setValue('2026-02-14')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(createApiKeyMock).toHaveBeenCalledWith({
      name: 'nightly build',
      permissions: ['api.impact.read'],
      expiresAt: '2026-02-14T00:00:00Z',
    })
    expect(wrapper.emitted('created')).toEqual([[createdKey]])
    expect(wrapper.get('input[type="text"]').element).toHaveProperty('value', '')
    wrapper.unmount()
  })

  it('shows API errors and re-enables submission', async () => {
    createApiKeyMock.mockRejectedValue(new Error('Permission denied'))
    const wrapper = mount(ApiKeyForm, { props: { settings } })

    await wrapper.get('input[type="text"]').setValue('nightly build')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('Permission denied')
    expect((wrapper.get('button[type="submit"]').element as HTMLButtonElement).disabled).toBe(false)
    expect(wrapper.emitted('created')).toBeUndefined()
    wrapper.unmount()
  })
})