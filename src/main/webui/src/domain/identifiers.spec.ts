import { describe, expect, it } from 'vitest'
import { idError, slugify } from './identifiers'

describe('identifiers', () => {
  it('derives an id from a name', () => {
    expect(slugify('  My Cluster (Préprod)! ')).toBe('my-cluster-preprod')
    expect(slugify('a_b--c')).toBe('a_b-c')
    expect(slugify('x'.repeat(80))).toHaveLength(64)
  })

  it('explains why an id is refused', () => {
    expect(idError('my-env_2')).toBeNull()
    expect(idError('')).toMatch(/required/)
    expect(idError('My Env')).toMatch(/Lowercase/)
    expect(idError('new')).toMatch(/reserved/)
    expect(idError('x'.repeat(65))).toMatch(/64/)
  })
})
