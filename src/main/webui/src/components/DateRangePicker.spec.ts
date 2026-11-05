import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import DateRangePicker from '@/components/DateRangePicker.vue'

const easepick = vi.hoisted(() => ({
  shouldThrow: false,
  options: [] as any[],
  instances: [] as any[],
}))

vi.mock('@easepick/bundle', () => {
  class DateTime {
    constructor(private readonly date: Date) {}

    toJSDate(): Date {
      return this.date
    }
  }

  class Picker {
    private readonly listeners = new Map<string, (event: any) => void>()
    setDateRange = vi.fn()
    destroy = vi.fn()

    constructor(options: any) {
      if (easepick.shouldThrow) throw new Error('Picker unavailable')
      easepick.options.push(options)
      easepick.instances.push(this)
      options.setup(this)
    }

    on(event: string, listener: (event: any) => void): void {
      this.listeners.set(event, listener)
    }

    select(detail: any): void {
      this.listeners.get('select')?.({detail})
    }
  }

  return {create: Picker, DateTime, LockPlugin: {}, PresetPlugin: {}, RangePlugin: {}}
})

const DAY_MS = 86_400_000

function dateLabel(value: {toJSDate: () => Date}): string {
  const date = value.toJSDate()
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function mountPicker(maxDays = 7) {
  return mount(DateRangePicker, {
    props: {from: '2025-01-01T00:00', to: '2025-01-08T00:00', maxSpanMs: maxDays * DAY_MS},
  })
}

describe('DateRangePicker', () => {
  beforeEach(() => {
    easepick.shouldThrow = false
    easepick.options.length = 0
    easepick.instances.length = 0
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn(() => ({matches: false})),
    })
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2025-03-31T23:30:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('builds UTC-based date presets from today', () => {
    const wrapper = mountPicker()
    const presets = easepick.options[0].PresetPlugin.customPreset as Record<string, Array<{toJSDate: () => Date}>>

    expect(Object.fromEntries(Object.entries(presets).map(([label, range]) => [label, range.map(dateLabel)]))).toEqual({
      Today: ['2025-03-31', '2025-03-31'],
      'Last 7 days': ['2025-03-25', '2025-03-31'],
      'Last 3 months': ['2025-01-01', '2025-03-31'],
      'Last 6 months': ['2024-10-01', '2025-03-31'],
      'Last year': ['2024-04-01', '2025-03-31'],
    })
    wrapper.unmount()
  })

  it('clamps a selected range to the maximum and emits an exclusive UTC end', () => {
    const wrapper = mountPicker()
    const picker = easepick.instances[0]
    const lockFilter = easepick.options[0].LockPlugin.filter as (date: any, picked: any[]) => boolean
    const anchor = {toJSDate: () => new Date(2025, 0, 1)}

    expect(lockFilter({toJSDate: () => new Date(2025, 0, 7)}, [anchor])).toBe(false)
    expect(lockFilter({toJSDate: () => new Date(2025, 0, 8)}, [anchor])).toBe(true)

    picker.select({
      start: {toJSDate: () => new Date(2025, 0, 1)},
      end: {toJSDate: () => new Date(2025, 0, 30)},
    })

    expect(wrapper.emitted('update:from')).toEqual([['2025-01-24T00:00']])
    expect(wrapper.emitted('update:to')).toEqual([['2025-01-31T00:00']])
    wrapper.unmount()
  })
})