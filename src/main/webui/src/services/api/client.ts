/** Status returned by Quarkus OIDC instead of an IdP redirect when an XHR hits an expired session. */
export const SESSION_EXPIRED_STATUS = 499

export type StatusMessages = Partial<Record<number, string>>

export interface ApiResponse<T> {
  status: { message: string } & Record<string, unknown>
  payload: T
}

export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

const DEFAULT_MESSAGES: StatusMessages = {
  401: 'Your session has expired, reload the page to sign in again.',
}

export function errorMessage(status: number, messages: StatusMessages = {}): string {
  return messages[status] ?? DEFAULT_MESSAGES[status] ?? `Unexpected error (${status}), try again.`
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  messages?: StatusMessages
}

export async function request<T>(url: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'X-Requested-With': 'JavaScript',
  }
  if (options.body !== undefined)
    headers['Content-Type'] = 'application/json'

  const response = await fetch(url, {
    method: options.method ?? 'GET',
    headers,
    credentials: 'same-origin',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  if (response.status === SESSION_EXPIRED_STATUS) {
    window.location.reload()
    throw new ApiError(response.status, errorMessage(401, options.messages))
  }
  if (!response.ok)
    throw new ApiError(response.status, errorMessage(response.status, options.messages))

  if (response.status === 204)
    return undefined as T
  const json = (await response.json()) as ApiResponse<T>
  return json.payload
}
