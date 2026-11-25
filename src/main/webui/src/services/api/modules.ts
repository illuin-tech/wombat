import type { CoreDeploymentStatus, JsonSchema, ModuleInfo, ModuleMetadata, ModulePrecedence, Redeployed } from '@/domain/module'
import { request, type StatusMessages } from '@/services/api/client'

export type { CoreDeploymentStatus, JsonSchema, ModuleInfo, ModuleMetadata, ModulePrecedence, Redeployed } from '@/domain/module'

const MODULE_MESSAGES: StatusMessages = {
  403: 'You are not allowed to manage modules.',
  404: 'This module is unknown to the server.',
}

function modulePath(type: string): string {
  return `/api/modules/${encodeURIComponent(type)}`
}

/** Every registered module, missing ones included. */
export function fetchModuleInfos(): Promise<ModuleInfo[]> {
  return request<ModuleInfo[]>('/api/modules')
}

/** The asset types this server knows. Missing modules take no asset, so they are left out. */
export async function fetchModules(): Promise<ModuleMetadata[]> {
  return (await fetchModuleInfos()).filter(module => !module.missing)
}

export function fetchModule(type: string): Promise<ModuleInfo> {
  return request<ModuleInfo>(modulePath(type), { messages: MODULE_MESSAGES })
}

/** The asset pickers follow at once; the core only follows on redeploy. */
export function setModuleEnabled(type: string, enabled: boolean): Promise<ModuleInfo> {
  return request<ModuleInfo>(modulePath(type), {
    method: 'PUT',
    body: { enabled },
    messages: { ...MODULE_MESSAGES, 409: 'No instance detects this module any more: it cannot be toggled.' },
  })
}

/** Sets the order of the modules, given every registered one: the core follows on redeploy. Returns them in their new order. */
export function setModuleOrder(modules: string[]): Promise<ModuleInfo[]> {
  return request<ModuleInfo[]>('/api/modules/order', {
    method: 'PUT',
    body: { modules },
    messages: { ...MODULE_MESSAGES, 400: 'The modules changed in the meantime: reload the page, then move the module again.' },
  })
}

/** Forgets a missing module. */
export function deleteModule(type: string): Promise<void> {
  return request<void>(modulePath(type), {
    method: 'DELETE',
    messages: { ...MODULE_MESSAGES, 409: 'An instance detects this module again: only a missing module can be deleted.' },
  })
}

/** Which family module serves first, for each family and component, deployed and after the next redeploy. */
export function fetchPrecedence(): Promise<ModulePrecedence> {
  return request<ModulePrecedence>('/api/modules/precedence')
}

export function fetchDeployment(): Promise<CoreDeploymentStatus> {
  return request<CoreDeploymentStatus>('/api/modules/deployment')
}

export function redeployCore(): Promise<Redeployed> {
  return request<Redeployed>('/api/modules/deployment', {
    method: 'POST',
    messages: { ...MODULE_MESSAGES, 409: 'Another redeploy was just requested: the instances converge on it.' },
  })
}

/* A type's schema only changes with the server's version: fetch it once per page load */
const schemas = new Map<string, Promise<JsonSchema>>()

export function fetchModuleSchema(type: string): Promise<JsonSchema> {
  let schema = schemas.get(type)
  if (!schema) {
    schema = request<JsonSchema>(`${modulePath(type)}/schema`, { messages: { 404: 'This asset type is unknown to the server.' } })
    schema.catch(() => schemas.delete(type))
    schemas.set(type, schema)
  }
  return schema
}
