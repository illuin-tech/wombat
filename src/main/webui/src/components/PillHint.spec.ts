import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import PillHint from './PillHint.vue'
import RegimePill from './RegimePill.vue'

/* Two pills side by side, as a table row lays them out */
const Row = defineComponent({
  setup: () => () => h('div', [
    h(PillHint, { hint: 'What the first one stands for' }, () => h('span', 'First')),
    h(PillHint, { hint: 'What the second one stands for' }, () => h('span', 'Second')),
  ]),
})

let wrapper: VueWrapper

function pills() {
  return wrapper.findAll('.pill-hint')
}

function bubbles(): string[] {
  return [...document.querySelectorAll('.pill-hint-bubble')].map(bubble => bubble.textContent ?? '')
}

/* Past the delay which leaves the pointer time to reach the bubble */
async function afterTheHideDelay(): Promise<void> {
  vi.advanceTimersByTime(200)
  await nextTick()
}

beforeEach(() => {
  vi.useFakeTimers()
  wrapper = mount(Row, { attachTo: document.body })
})

afterEach(() => {
  wrapper.unmount()
  document.body.innerHTML = ''
  vi.useRealTimers()
})

describe('PillHint', () => {
  it('explains the pill while hovered', async () => {
    await pills()[0].trigger('mouseenter')
    expect(bubbles()).toEqual(['What the first one stands for'])
    expect(pills()[0].attributes('aria-describedby')).toBe(document.querySelector('.pill-hint-bubble')!.id)

    await pills()[0].trigger('mouseleave')
    await afterTheHideDelay()
    expect(bubbles()).toEqual([])
    expect(pills()[0].attributes('aria-describedby')).toBeUndefined()
  })

  it('stays while the pointer is over the explanation', async () => {
    await pills()[0].trigger('mouseenter')
    await pills()[0].trigger('mouseleave')
    document.querySelector('.pill-hint-bubble')!.dispatchEvent(new MouseEvent('mouseenter'))
    await afterTheHideDelay()
    expect(bubbles()).toEqual(['What the first one stands for'])

    document.querySelector('.pill-hint-bubble')!.dispatchEvent(new MouseEvent('mouseleave'))
    await afterTheHideDelay()
    expect(bubbles()).toEqual([])
  })

  it('explains the pill while focused, until Escape', async () => {
    await pills()[1].trigger('focus')
    expect(bubbles()).toEqual(['What the second one stands for'])

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()
    expect(bubbles()).toEqual([])
  })

  it('keeps the title of the pill from showing a second tooltip', () => {
    const hinted = mount(PillHint, { props: { hint: 'Measured on a running system' }, slots: { default: () => h(RegimePill, { regime: 'MEASURED' }) } })

    expect(hinted.find('.icon-pill').attributes('title')).toBeUndefined()
    expect(mount(RegimePill, { props: { regime: 'MEASURED' } }).attributes('title')).toBe('Its activity is measured on a running system')
    hinted.unmount()
  })

  it('shows one explanation at a time', async () => {
    await pills()[0].trigger('focus')
    await pills()[1].trigger('mouseenter')

    expect(bubbles()).toEqual(['What the second one stands for'])
  })
})
