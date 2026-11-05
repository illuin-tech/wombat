import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { ApiKeyInfo, ApiKeySettings } from '@/domain/access'
import type { AppInfo } from '@/domain/app'
import { createApiKey, fetchApiKeySettings, fetchApiKeys, revokeApiKey } from '@/services/api/apiKeys'
import ApiKeysPage from './ApiKeysPage.vue'

const sessionMocks = vi.hoisted(() => ({
  loadAppInfo: vi.fn(),
  setAppInfo: (_info: unknown): void => {},
}))

vi.mock('@/services/auth/session', async () => {
  const { readonly, ref } = await import('vue')
  const appInfo = ref<unknown>(null)
  sessionMocks.setAppInfo = (info) => { appInfo.value = info }

  return {
    loadAppInfo: sessionMocks.loadAppInfo,
    useAppInfo: () => ({ appInfo: readonly(appInfo) }),
  }
})

vi.mock('@/services/api/apiKeys', () => ({
  createApiKey: vi.fn(),
  fetchApiKeySettings: vi.fn(),
  fetchApiKeys: vi.fn(),
  revokeApiKey: vi.fn(),
}))

const fetchApiKeySettingsMock = vi.mocked(fetchApiKeySettings)
const fetchApiKeysMock = vi.mocked(fetchApiKeys)
const createApiKeyMock = vi.mocked(createApiKey)
const revokeApiKeyMock = vi.mocked(revokeApiKey)

const settings: ApiKeySettings = {
  grantablePermissions: ['api.impact.read'],
  maxLifetimeDays: 60,
  allowNeverExpiring: true,
}
const ownKey: ApiKeyInfo = {
  id: 'own-key',
  name: 'Personal automation',
  permissions: ['api.impact.read'],
  owner: 'Alice',
  createdAt: '2026-01-01T00:00:00Z',
  expiresAt: '2099-01-01T00:00:00Z',
  revokedAt: null,
  lastUsedAt: null,
}
const adminKey: ApiKeyInfo = {
  ...ownKey,
  id: 'admin-key',
  name: 'Other user key',
  owner: 'Bob',
}
const createdKey: ApiKeyInfo = {
  ...ownKey,
  id: 'created-key',
  name: 'nightly pipeline',
  expiresAt: null,
  token: 'new-secret-token',
}

function appInfo(authEnabled = true, permissions: string[] = []): AppInfo {
  return {
    auth: { enabled: authEnabled },
    user: { authenticated: authEnabled, name: 'Alice', roles: [], permissions },
    ui: { maxDateRange: { millis: 86400000, label: '1 day' } },
  }
}

beforeEach(() => {
  vi.resetAllMocks()
  sessionMocks.setAppInfo(null)
  sessionMocks.loadAppInfo.mockImplementation(async () => {
    const info = appInfo()
    sessionMocks.setAppInfo(info)
    return info
  })
  fetchApiKeySettingsMock.mockResolvedValue(settings)
  fetchApiKeysMock.mockResolvedValue([])
  createApiKeyMock.mockResolvedValue(createdKey)
  revokeApiKeyMock.mockResolvedValue(undefined)
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('ApiKeysPage API integration', () => {
  it('skips API-key clients when authentication is disabled', async () => {
    const disabledInfo = appInfo(false)
    sessionMocks.loadAppInfo.mockImplementation(async () => {
      sessionMocks.setAppInfo(disabledInfo)
      return disabledInfo
    })
    const wrapper = mount(ApiKeysPage)

    await flushPromises()

    expect(wrapper.get('.notice-title').text()).toBe('Authentication is disabled')
    expect(fetchApiKeySettingsMock).not.toHaveBeenCalled()
    expect(fetchApiKeysMock).not.toHaveBeenCalled()
    expect(createApiKeyMock).not.toHaveBeenCalled()
    expect(revokeApiKeyMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('loads settings and keys, then creates and revokes against the selected all-keys list', async () => {
    const adminInfo = appInfo(true, ['api.apikey.admin'])
    sessionMocks.loadAppInfo.mockImplementation(async () => {
      sessionMocks.setAppInfo(adminInfo)
      return adminInfo
    })
    fetchApiKeysMock
      .mockResolvedValueOnce([ownKey])
      .mockResolvedValueOnce([adminKey])
      .mockResolvedValueOnce([createdKey])
      .mockResolvedValueOnce([])
    const wrapper = mount(ApiKeysPage)

    await flushPromises()

    expect(fetchApiKeySettingsMock).toHaveBeenCalledOnce()
    expect(fetchApiKeysMock).toHaveBeenNthCalledWith(1, false)
    expect(wrapper.findAll('.table-card tbody .name').map((name) => name.text())).toEqual(['Personal automation'])

    await wrapper.findAll('.table-card .apikey-check input')[1].setValue(true)
    await flushPromises()

    expect(fetchApiKeysMock).toHaveBeenNthCalledWith(2, true)
    expect(wrapper.get('.table-head').text()).toContain('All keys')
    expect(wrapper.findAll('.table-card tbody .name').map((name) => name.text())).toEqual(['Other user key'])

    await wrapper.get('.apikey-form input[type="text"]').setValue('  nightly pipeline  ')
    await wrapper.get('.apikey-form .apikey-expiry input[type="checkbox"]').setValue(true)
    await wrapper.get('.apikey-form').trigger('submit')
    await flushPromises()

    expect(createApiKeyMock).toHaveBeenCalledWith({
      name: 'nightly pipeline',
      permissions: ['api.impact.read'],
      expiresAt: null,
    })
    expect(fetchApiKeysMock).toHaveBeenNthCalledWith(3, true)
    expect(wrapper.get('.apikey-token-value').text()).toBe('new-secret-token')
    expect(wrapper.findAll('.table-card tbody .name').map((name) => name.text())).toEqual(['nightly pipeline'])

    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    await wrapper.get('.table-card .btn-danger').trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenCalledWith('Revoke "nightly pipeline"? Anything using it will stop working immediately.')
    expect(revokeApiKeyMock).toHaveBeenCalledWith('created-key')
    expect(fetchApiKeysMock).toHaveBeenNthCalledWith(4, true)
    expect(wrapper.get('.apikey-empty').text()).toBe('No keys yet.')
    wrapper.unmount()
  })

  it('shows API and mutation errors without refreshing after failed mutations', async () => {
    fetchApiKeysMock.mockResolvedValueOnce([ownKey])
    createApiKeyMock.mockRejectedValueOnce(new Error('Creation denied'))
    revokeApiKeyMock.mockRejectedValueOnce(new Error('Revocation denied'))
    const wrapper = mount(ApiKeysPage)

    await flushPromises()

    expect(fetchApiKeysMock).toHaveBeenCalledOnce()
    await wrapper.get('.apikey-form input[type="text"]').setValue('nightly pipeline')
    await wrapper.get('.apikey-form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('.apikey-form [role="alert"]').text()).toBe('Creation denied')
    expect(fetchApiKeysMock).toHaveBeenCalledOnce()

    vi.spyOn(window, 'confirm').mockReturnValue(true)
    await wrapper.get('.table-card .btn-danger').trigger('click')
    await flushPromises()

    expect(revokeApiKeyMock).toHaveBeenCalledWith('own-key')
    expect(wrapper.get('.notice-title').text()).toBe('API key error')
    expect(wrapper.text()).toContain('Revocation denied')
    expect(fetchApiKeysMock).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('displays errors returned while loading the key list', async () => {
    fetchApiKeysMock.mockRejectedValueOnce(new Error('Keys unavailable'))
    const wrapper = mount(ApiKeysPage)

    await flushPromises()

    expect(wrapper.get('.notice-title').text()).toBe('API key error')
    expect(wrapper.text()).toContain('Keys unavailable')
    wrapper.unmount()
  })
})
