import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import type { OutlineProperty } from '@/pages/modules/model/schemaOutline'
import SchemaOutline from './SchemaOutline.vue'

function property(name: string, overrides: Partial<OutlineProperty> = {}): OutlineProperty {
  return { name, label: name, type: 'string', required: false, constraints: [], values: [], properties: [], variants: [], ...overrides }
}

describe('SchemaOutline', () => {
  it('shows each property with its name, type, requirement, constraints and default', () => {
    const outline = mount(SchemaOutline, {
      props: { properties: [property('outputTokensPerRequest', { label: 'Output tokens per request', type: 'integer', required: true, constraints: ['> 0'], defaultValue: '500' })] },
    })

    const head = outline.find('.schema-property-head')
    expect(head.find('.schema-label').text()).toBe('Output tokens per request')
    expect(head.find('.tech-name').text()).toBe('outputTokensPerRequest')
    expect(head.find('.schema-type').text()).toBe('integer')
    expect(head.find('.schema-required').exists()).toBe(true)
    expect(head.find('.schema-constraint').text()).toBe('> 0')
    expect(head.find('.schema-default').text()).toBe('default 500')
  })

  it('folds long value lists, showing titles over values', () => {
    const countries = Array.from({ length: 10 }, (_, index) => ({ value: `C${index}`, title: `Country ${index}` }))
    const outline = mount(SchemaOutline, { props: { properties: [property('location', { values: countries })] } })

    const shown = outline.findAll('.schema-values > .tech-name')
    expect(shown).toHaveLength(8)
    expect(shown[0].text()).toBe('Country 0')
    expect(shown[0].attributes('title')).toBe('C0')
    expect(outline.find('.schema-more summary').text()).toBe('and 2 more')
  })

  it('nests the properties of an object, and the alternatives of a union with their discriminator', () => {
    const outline = mount(SchemaOutline, {
      props: {
        properties: [
          property('profile', { type: 'object', properties: [property('zoneId')] }),
          property('scenarios', {
            type: 'list of alternatives',
            variants: [{ title: 'Constant', discriminator: { name: 'type', value: 'constant' }, properties: [property('load')] }],
          }),
        ],
      },
    })

    expect(outline.find('.schema-nested summary').text()).toBe('1 property')
    expect(outline.find('.schema-nested .schema-label').text()).toBe('zoneId')
    const variant = outline.find('.schema-variant')
    expect(variant.findAll('.schema-label').map(label => label.text())).toEqual(['Constant', 'load'])
    expect(variant.find('summary .tech-name').text()).toBe('type: constant')
  })
})
