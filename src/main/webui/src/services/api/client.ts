/** Status returned by Quarkus OIDC instead of an IdP redirect when an XHR hits an expired session. */
export const SESSION_EXPIRED_STATUS = 499

export type StatusMessages = Partial<Record<number, string>>

export interface ApiResponse<T> {
  status: { message: string } & Record<string, unknown>
  payload: T
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly reason: string | null = null,
    /** The server's own explanation, e.g. which property is invalid, when it gives one. */
    readonly serverMessage: string | null = null,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

interface ErrorDetails {
  reason: string | null
  serverMessage: string | null
}

async function errorDetails(response: Response): Promise<ErrorDetails> {
  try {
    const json = (await response.json()) as Partial<ApiResponse<unknown>>
    const metadata = json.status?.metadata
    const reason = metadata && typeof metadata === 'object' && 'reason' in metadata && typeof metadata.reason === 'string' ? metadata.reason : null
    const serverMessage = typeof json.status?.message === 'string' && json.status.message ? json.status.message : null
    return { reason, serverMessage }
  } catch {
    return { reason: null, serverMessage: null }
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
  if (!response.ok) {
    const { reason, serverMessage } = await errorDetails(response)
    throw new ApiError(response.status, errorMessage(response.status, options.messages), reason, serverMessage)
  }

  if (response.status === 204)
    return undefined as T
  const json = (await response.json()) as ApiResponse<T>
  return json.payload
}

/** What to tell the user about a failed call: the server's explanation of a refused request, otherwise the module's message. */
export function errorText(error: unknown): string {
  if (error instanceof ApiError)
    return (error.status === 400 || error.status === 409) && error.serverMessage ? error.serverMessage : error.message
  return error instanceof Error ? error.message : String(error)
}
