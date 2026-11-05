import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import type {ChartData, ChartOptions} from 'chart.js'
import ChartCanvas from '@/components/ChartCanvas.vue'

const chartState = vi.hoisted(() => ({
  register: vi.fn(),
  instances: [] as any[],
  defaults: {
    color: '',
    borderColor: '',
    font: {family: '', size: 0, weight: 0},
  },
}))

vi.mock('chart.js', () => {
  class Chart {
    static register = chartState.register
    static defaults = chartState.defaults
    destroy = vi.fn()

    constructor(readonly canvas: HTMLCanvasElement, readonly config: any) {
      chartState.instances.push(this)
    }
  }

  return {Chart, registerables: []}
})

vi.mock('@/layout/theme/useTheme', () => ({
  useTheme: () => ({isDark: {value: false}}),
}))

describe('ChartCanvas', () => {
  beforeEach(() => {
    chartState.instances.length = 0
    chartState.register.mockClear()
    chartState.defaults.color = ''
    chartState.defaults.borderColor = ''
    chartState.defaults.font = {family: '', size: 0, weight: 0}
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('creates a chart, replaces it when its data changes, and destroys it on unmount', async () => {
    const data: ChartData<'bar'> = {labels: ['Jan'], datasets: [{label: 'GWP', data: [1]}]}
    const updatedData: ChartData<'bar'> = {labels: ['Feb'], datasets: [{label: 'GWP', data: [2]}]}
    const options: ChartOptions<'bar'> = {responsive: false}
    const wrapper = mount(ChartCanvas, {props: {type: 'bar', data, options}})

    expect(chartState.instances).toHaveLength(1)
    expect(chartState.instances[0].canvas).toBe(wrapper.get('canvas').element)
    expect(chartState.instances[0].config).toEqual({type: 'bar', data, options})

    await wrapper.setProps({data: updatedData})
    expect(chartState.instances).toHaveLength(2)
    expect(chartState.instances[0].destroy).toHaveBeenCalledOnce()
    expect(chartState.instances[1].config.data).toEqual(updatedData)

    wrapper.unmount()
    expect(chartState.instances[1].destroy).toHaveBeenCalledOnce()
  })
})