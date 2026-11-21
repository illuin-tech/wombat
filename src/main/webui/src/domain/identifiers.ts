/** Ids of environments and assets created through the API, as the server checks them. */
export const ID_PATTERN = /^[a-z0-9_-]+$/
export const ID_MAX_LENGTH = 64
/** The path segment of the creation pages. */
const RESERVED_IDS = new Set(['new'])

/** A valid id derived from a name, e.g. `my-cluster` from `My Cluster!`. */
export function slugify(name: string): string {
  return name
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9_-]+/g, '-')
    .replace(/-{2,}/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, ID_MAX_LENGTH)
}

/** Why the id would be refused, null when it is valid. */
export function idError(id: string): string | null {
  if (!id) return 'An id is required.'
  if (id.length > ID_MAX_LENGTH) return `At most ${ID_MAX_LENGTH} characters.`
  if (!ID_PATTERN.test(id)) return 'Lowercase letters, digits, dashes and underscores only.'
  if (RESERVED_IDS.has(id)) return `"${id}" is reserved.`
  return null
}

export const NAME_MAX_LENGTH = 255
export const DESCRIPTION_MAX_LENGTH = 4096
export const NOTES_MAX_LENGTH = 65536

/** Why the name would be refused, null when it is valid. */
export function nameError(name: string): string | null {
  if (!name.trim()) return 'A name is required.'
  if (name.length > NAME_MAX_LENGTH) return `At most ${NAME_MAX_LENGTH} characters.`
  return null
}

export function descriptionError(description: string): string | null {
  return description.length > DESCRIPTION_MAX_LENGTH ? `At most ${DESCRIPTION_MAX_LENGTH} characters.` : null
}

export function notesError(notes: string): string | null {
  return notes.length > NOTES_MAX_LENGTH ? `At most ${NOTES_MAX_LENGTH} characters.` : null
}
