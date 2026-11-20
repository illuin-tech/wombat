import type { EnvironmentInfo, LiveEnvironment } from '@/domain/environment'
import { request, type StatusMessages } from '@/services/api/client'

export type { AssetInfo, EnvironmentInfo, LiveEnvironment } from '@/domain/environment'

export const ENVIRONMENT_MESSAGES: StatusMessages = {
  400: 'The environment was refused: check its id, name and description.',
  403: 'You are not allowed to edit environments.',
  404: 'This environment no longer exists.',
  409: 'This environment cannot be changed.',
}

/** The editable properties of an environment: an update replaces all of them. */
export interface EnvironmentFields {
  name: string
  description: string | null
  disabled: boolean
}

export interface EnvironmentCreation extends EnvironmentFields {
  id: string
}

/** The live context includes only environments and assets recognized by the evaluator. */
export function fetchLiveEnvironments(): Promise<LiveEnvironment[]> {
  return request<LiveEnvironment[]>('/api/impact/environments')
}

/** Every environment record, enabled or not, with its undeleted assets. */
export function fetchEnvironments(): Promise<EnvironmentInfo[]> {
  return request<EnvironmentInfo[]>('/api/environments')
}

function environmentPath(id: string): string {
  return `/api/environments/${encodeURIComponent(id)}`
}

export function fetchEnvironment(id: string): Promise<EnvironmentInfo> {
  return request<EnvironmentInfo>(environmentPath(id), { messages: ENVIRONMENT_MESSAGES })
}

export function createEnvironment(body: EnvironmentCreation): Promise<EnvironmentInfo> {
  return request<EnvironmentInfo>('/api/environments', {
    method: 'POST',
    body,
    messages: { ...ENVIRONMENT_MESSAGES, 409: 'An environment with this id already exists.' },
  })
}

export function updateEnvironment(id: string, body: EnvironmentFields): Promise<EnvironmentInfo> {
  return request<EnvironmentInfo>(environmentPath(id), {
    method: 'PUT',
    body,
    messages: { ...ENVIRONMENT_MESSAGES, 409: 'This environment is provisioned: it cannot be edited.' },
  })
}

export function deleteEnvironment(id: string): Promise<void> {
  return request<void>(environmentPath(id), {
    method: 'DELETE',
    messages: { ...ENVIRONMENT_MESSAGES, 409: 'This environment is provisioned or still has assets.' },
  })
}
