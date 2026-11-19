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

    getTime(): number {
      return this.date.getTime()
    }
  }

  class Picker {
    private readonly listeners = new Map<string, (event: any) => void>()
    setDateRange = vi.fn()
    destroy = vi.fn()
    ui = {container: document.createElement('div')}

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

    /** Renders the preset buttons the way PresetPlugin does: timestamps of the range in data attributes. */
    renderPresets(): HTMLElement[] {
      const presets = easepick.options.at(-1).PresetPlugin.customPreset as Record<string, Array<{getTime: () => number}>>
      return Object.entries(presets).map(([label, [start, end]]) => {
        const button = document.createElement('button')
        button.className = 'preset-button unit'
        button.textContent = label
        button.dataset.start = String(start.getTime())
        button.dataset.end = String(end.getTime())
        this.ui.container.appendChild(button)
        this.listeners.get('view')?.({detail: {view: 'PresetPluginButton', target: button}})
        return button
      })
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

function mountPicker(maxDays = 7, from = '2025-01-01T00:00', to = '2025-01-08T00:00') {
  return mount(DateRangePicker, {
    props: {from, to, maxSpanMs: maxDays * DAY_MS},
  })
}

function activePresets(buttons: HTMLElement[]): string[] {
  return buttons.filter(button => button.classList.contains('active')).map(button => button.textContent ?? '')
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

  it('highlights the preset matching the current range, and none for a custom one', async () => {
    const wrapper = mountPicker(400, '2025-03-25T00:00', '2025-04-01T00:00')
    const buttons: HTMLElement[] = easepick.instances[0].renderPresets()
    expect(activePresets(buttons)).toEqual(['Last 7 days'])
    expect(buttons.find(button => button.textContent === 'Last 7 days')?.getAttribute('aria-pressed')).toBe('true')

    await wrapper.setProps({from: '2025-03-31T00:00', to: '2025-04-01T00:00'})
    expect(activePresets(buttons)).toEqual(['Today'])

    await wrapper.setProps({from: '2025-03-20T00:00', to: '2025-03-27T00:00'})
    expect(activePresets(buttons)).toEqual([])
    wrapper.unmount()
  })
})
