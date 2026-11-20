import type { JsonSchema, ModuleMetadata } from '@/domain/module'
import { request } from '@/services/api/client'

export type { JsonSchema, ModuleMetadata } from '@/domain/module'

export function fetchModules(): Promise<ModuleMetadata[]> {
  return request<ModuleMetadata[]>('/api/modules')
}

/* A type's schema only changes with the server's version: fetch it once per page load */
const schemas = new Map<string, Promise<JsonSchema>>()

export function fetchModuleSchema(type: string): Promise<JsonSchema> {
  let schema = schemas.get(type)
  if (!schema) {
    schema = request<JsonSchema>(`/api/modules/${encodeURIComponent(type)}/schema`, { messages: { 404: 'This asset type is unknown to the server.' } })
    schema.catch(() => schemas.delete(type))
    schemas.set(type, schema)
  }
  return schema
}
