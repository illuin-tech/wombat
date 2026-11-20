type Schema = Record<string, unknown>

const LOCAL_DEFINITION = /^#\/\$defs\/(.+)$/

/**
 * The schema with its local `$ref`s replaced by the definitions they point to: JSON Forms validates each variant of a
 * `oneOf` on its own, where a reference to the root's `$defs` cannot resolve. A recursive definition keeps its `$ref`.
 */
export function inlineDefinitions(schema: Schema): Schema {
  const definitions = (schema.$defs ?? {}) as Record<string, Schema>

  function inline(node: unknown, resolving: readonly string[]): unknown {
    if (Array.isArray(node))
      return node.map(item => inline(item, resolving))
    if (node === null || typeof node !== 'object')
      return node

    const { $ref, ...rest } = node as Schema
    const name = typeof $ref === 'string' ? LOCAL_DEFINITION.exec($ref)?.[1] : undefined
    if (name !== undefined && definitions[name] && !resolving.includes(name)) {
      const definition = inline(definitions[name], [...resolving, name]) as Schema
      // The referencing node's own keywords, e.g. its description, are the more specific ones
      return { ...definition, ...(inline(rest, resolving) as Schema) }
    }
    return Object.fromEntries(Object.entries(node as Schema).map(([key, value]) => [key, inline(value, resolving)]))
  }

  const { $defs, ...root } = schema
  const inlined = inline(root, []) as Schema
  return JSON.stringify(inlined).includes('"$ref":"#/$defs/') ? { ...inlined, $defs } : inlined
}

/**
 * The properties a new asset starts with: every default of the schema, within the objects it requires, so that an
 * object whose properties all have defaults, e.g. a profile, is valid without being edited.
 */
export function defaultProperties(schema: Schema): Record<string, unknown> {
  function defaults(node: Schema): unknown {
    if (node.default !== undefined)
      return structuredClone(node.default)
    if (node.type !== 'object' || !node.properties)
      return undefined
    const required = new Set((node.required ?? []) as string[])
    const value: Record<string, unknown> = {}
    for (const [name, property] of Object.entries(node.properties as Record<string, Schema>)) {
      const propertyDefault = property.type === 'object' && !required.has(name) && property.default === undefined
        ? undefined
        : defaults(property)
      if (propertyDefault !== undefined) value[name] = propertyDefault
    }
    return value
  }
  return (defaults(inlineDefinitions(schema)) ?? {}) as Record<string, unknown>
}
