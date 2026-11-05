import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { UserInfo } from '@/domain/access'
import type { AppInfo } from '@/domain/app'
import { fetchUsers, grantRole, removeRole } from '@/services/api/users'
import UsersPage from './UsersPage.vue'

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

vi.mock('@/services/api/users', () => ({
  fetchUsers: vi.fn(),
  grantRole: vi.fn(),
  removeRole: vi.fn(),
}))

const fetchUsersMock = vi.mocked(fetchUsers)
const grantRoleMock = vi.mocked(grantRole)
const removeRoleMock = vi.mocked(removeRole)

const user: UserInfo = {
  id: 'user-1',
  name: 'Lena Martin',
  email: 'lena@example.test',
  self: false,
  createdAt: '2026-01-01T00:00:00Z',
  lastSeenAt: '2026-02-01T00:00:00Z',
  roles: [{
    role: 'viewer',
    fromProvider: false,
    grant: { grantedBy: 'owner', grantedAt: '2026-01-02T00:00:00Z' },
    removable: true,
  }],
  grantableRoles: ['operator'],
}
const userWithOperator: UserInfo = {
  ...user,
  roles: [...user.roles, {
    role: 'operator',
    fromProvider: false,
    grant: { grantedBy: 'owner', grantedAt: '2026-02-02T00:00:00Z' },
    removable: true,
  }],
  grantableRoles: ['admin'],
}
const userForRemoval: UserInfo = {
  ...userWithOperator,
  grantableRoles: [],
}
const userAfterRemoval: UserInfo = {
  ...user,
  grantableRoles: ['operator'],
}

function appInfo(authEnabled = true): AppInfo {
  return {
    auth: { enabled: authEnabled },
    user: { authenticated: authEnabled, name: 'Alice', roles: [], permissions: ['api.user.manage'] },
    ui: { maxDateRange: { millis: 86400000, label: '1 day' } },
  }
}

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

beforeEach(() => {
  vi.resetAllMocks()
  sessionMocks.setAppInfo(null)
  sessionMocks.loadAppInfo.mockImplementation(async () => {
    const info = appInfo()
    sessionMocks.setAppInfo(info)
    return info
  })
  fetchUsersMock.mockResolvedValue([])
  grantRoleMock.mockResolvedValue(undefined)
  removeRoleMock.mockResolvedValue(undefined)
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('UsersPage API integration', () => {
  it('skips the users client when authentication is disabled', async () => {
    const disabledInfo = appInfo(false)
    sessionMocks.loadAppInfo.mockImplementation(async () => {
      sessionMocks.setAppInfo(disabledInfo)
      return disabledInfo
    })
    const wrapper = mount(UsersPage)

    await flushPromises()

    expect(wrapper.get('.notice-title').text()).toBe('Authentication is disabled')
    expect(fetchUsersMock).not.toHaveBeenCalled()
    expect(grantRoleMock).not.toHaveBeenCalled()
    expect(removeRoleMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('loads users, grants the selected role, and refreshes the displayed list', async () => {
    fetchUsersMock.mockResolvedValueOnce([user]).mockResolvedValueOnce([userWithOperator])
    const grantPending = deferred<void>()
    grantRoleMock.mockReturnValueOnce(grantPending.promise)
    const wrapper = mount(UsersPage)

    await flushPromises()

    expect(fetchUsersMock).toHaveBeenCalledOnce()
    expect(wrapper.findAll('.table-card tbody .name').map((name) => name.text())).toEqual(['Lena Martin'])
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)

    await wrapper.get('.user-grant').trigger('click')

    expect(confirm).toHaveBeenCalledWith('Grant the operator role to Lena Martin?')
    expect(grantRoleMock).toHaveBeenCalledWith('user-1', 'operator')
    expect((wrapper.get('.user-grant').element as HTMLButtonElement).disabled).toBe(true)
    expect((wrapper.get('.user-role-remove').element as HTMLButtonElement).disabled).toBe(true)

    grantPending.resolve(undefined)
    await flushPromises()

    expect(fetchUsersMock).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.user-roles').text()).toContain('operator')
    expect(wrapper.get('.user-grant').text()).toContain('admin')
    expect((wrapper.get('.user-grant').element as HTMLButtonElement).disabled).toBe(false)
    wrapper.unmount()
  })

  it('removes a confirmed role using the user ID and refreshes the list', async () => {
    fetchUsersMock.mockResolvedValueOnce([userForRemoval]).mockResolvedValueOnce([userAfterRemoval])
    const wrapper = mount(UsersPage)

    await flushPromises()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    await wrapper.get('[aria-label="Remove role operator from Lena Martin"]').trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenCalledWith('Remove the operator role granted to Lena Martin?')
    expect(removeRoleMock).toHaveBeenCalledWith('user-1', 'operator')
    expect(fetchUsersMock).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.user-roles').text()).not.toContain('operator')
    expect(wrapper.get('.user-grant').text()).toContain('operator')
    wrapper.unmount()
  })

  it('does not mutate or refresh when either role change is cancelled', async () => {
    fetchUsersMock.mockResolvedValueOnce([user])
    const wrapper = mount(UsersPage)

    await flushPromises()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    await wrapper.get('.user-grant').trigger('click')
    await wrapper.get('[aria-label="Remove role viewer from Lena Martin"]').trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenNthCalledWith(1, 'Grant the operator role to Lena Martin?')
    expect(confirm).toHaveBeenNthCalledWith(2, 'Remove the viewer role granted to Lena Martin?')
    expect(grantRoleMock).not.toHaveBeenCalled()
    expect(removeRoleMock).not.toHaveBeenCalled()
    expect(fetchUsersMock).toHaveBeenCalledOnce()
    expect(wrapper.find('.user-roles').text()).toContain('viewer')
    wrapper.unmount()
  })

  it('shows mutation errors and clears pending state without refreshing', async () => {
    fetchUsersMock.mockResolvedValueOnce([user])
    grantRoleMock.mockRejectedValueOnce(new Error('Grant denied'))
    const wrapper = mount(UsersPage)

    await flushPromises()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    await wrapper.get('.user-grant').trigger('click')
    await flushPromises()

    expect(wrapper.get('.notice-title').text()).toBe('Users error')
    expect(wrapper.text()).toContain('Grant denied')
    expect((wrapper.get('.user-grant').element as HTMLButtonElement).disabled).toBe(false)
    expect(fetchUsersMock).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('shows refresh errors after a successful role mutation', async () => {
    fetchUsersMock.mockResolvedValueOnce([user]).mockRejectedValueOnce(new Error('Refresh unavailable'))
    const wrapper = mount(UsersPage)

    await flushPromises()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    await wrapper.get('.user-grant').trigger('click')
    await flushPromises()

    expect(grantRoleMock).toHaveBeenCalledWith('user-1', 'operator')
    expect(fetchUsersMock).toHaveBeenCalledTimes(2)
    expect(wrapper.get('.notice-title').text()).toBe('Users error')
    expect(wrapper.text()).toContain('Refresh unavailable')
    expect(wrapper.text()).toContain('No one has signed in yet.')
    wrapper.unmount()
  })
})
