import {describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import {defineComponent} from 'vue'
import type {TimelineResponse} from '@/domain/impact'
import TimelineChart from '@/pages/impact/components/TimelineChart.vue'

vi.mock('@/layout/theme/useTheme', () => ({
  useTheme: () => ({isDark: {value: false}}),
}))

const ChartCanvasStub = defineComponent({
  name: 'ChartCanvas',
  props: ['type', 'data', 'options'],
  template: '<canvas />',
})

describe('TimelineChart tooltips', () => {
  it('formats nullable parsed values as zero in labels and totals', () => {
    const timeline: TimelineResponse = {
      stepMs: 86_400_000,
      stepLabel: 'day',
      stepPlural: 'days',
      bucketStarts: [Date.UTC(2025, 0, 1)],
      series: [{key: 'gwp', label: 'GWP', unit: 'kgCO2eq', buckets: [{embedded: 1, use: 2}]}],
    }
    const wrapper = mount(TimelineChart, {
      props: {timeline},
      global: {stubs: {ChartCanvas: ChartCanvasStub}},
    })
    const options = wrapper.findComponent(ChartCanvasStub).props('options') as any
    const callbacks = options.plugins.tooltip.callbacks

    expect(callbacks.label({dataset: {label: 'Use'}, parsed: {y: null}})).toBe(' Use: 0 kgCO2eq')
    expect(callbacks.footer([{parsed: {y: null}}, {parsed: {y: 2}}])).toBe('Total: 2 kgCO2eq')
    wrapper.unmount()
  })
})