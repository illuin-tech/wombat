import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import Modal from '../../main/frontend/components/Modal.vue';
import Picker from '../../main/frontend/components/Picker.vue';
import Filters from '../../main/frontend/components/Filters.vue';
import ThemeToggle from '../../main/frontend/components/ThemeToggle.vue';
import ChartCard from '../../main/frontend/components/ChartCard.vue';
import TimelineChart from '../../main/frontend/components/TimelineChart.vue';
import ServiceRow from '../../main/frontend/components/ServiceRow.vue';
import DateRange from '../../main/frontend/components/DateRange.vue';

const wrappers = [];
function render(component, options) {
  const wrapper = mount(component, options);
  wrappers.push(wrapper);
  return wrapper;
}
afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount());
  document.body.replaceChildren();
  document.body.className = '';
  localStorage.clear();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('Vue component contracts', () => {
  it('moves inert server text into the modal, traps focus, closes with Escape and restores focus', async () => {
    const trigger = document.createElement('button');
    const node = document.createElement('p');
    node.textContent = '{{ globalThis.unsafe = true }} <script>';
    document.body.append(trigger);
    const wrapper = render(Modal, {
      attachTo: document.body,
      props: { id: 'asset', title: '<name>', regime: 'Measured', nodes: [node], trigger }
    });
    expect(wrapper.find('.asset-modal-overlay').isVisible()).toBe(false);
    trigger.click();
    await nextTick();
    expect(wrapper.find('.asset-modal-overlay').isVisible()).toBe(true);
    expect(wrapper.find('.asset-modal-body p').element).toBe(node);
    expect(wrapper.find('.asset-modal-heading').text()).toBe('<name>Measured');
    expect(globalThis.unsafe).toBeUndefined();
    const close = wrapper.find('.asset-modal-close');
    expect(document.activeElement).toBe(close.element);
    await close.trigger('keydown', { key: 'Tab' });
    expect(document.activeElement).toBe(close.element);
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await nextTick();
    await nextTick();
    expect(wrapper.find('.asset-modal-overlay').isVisible()).toBe(false);
    expect(document.body.classList.contains('modal-open')).toBe(false);
    expect(document.activeElement).toBe(trigger);
    await wrapper.find('.asset-modal-close').trigger('click');
    wrapper.unmount();
    trigger.click();
    expect(document.body.classList.contains('modal-open')).toBe(false);
  });

  it('searches and changes only visible services with bulk selection', async () => {
    const wrapper = render(Picker, {
      props: {
        kind: 'services', prefix: 'service', searchable: true,
        options: [{ value: 'api', label: 'API' }, { value: 'worker', label: 'Worker' }],
        modelValue: ['api', 'worker'],
        'onUpdate:modelValue': values => wrapper.setProps({ modelValue: values })
      }
    });
    await wrapper.find('button').trigger('click');
    await wrapper.find('input[type=text]').setValue('work');
    await wrapper.find('#btnSelectNone').trigger('click');
    expect(wrapper.props('modelValue')).toEqual(['api']);
    expect(wrapper.find('#serviceCount').text()).toBe('1 / 2 services');
    await wrapper.find('#btnSelectAll').trigger('click');
    expect(wrapper.props('modelValue')).toEqual(['api', 'worker']);
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await nextTick();
    expect(wrapper.find('.container-trigger').attributes('aria-expanded')).toBe('false');
  });

  it('preserves exclusive UTC dates, compact filters, and environment/asset reset navigation', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    const navigate = vi.fn();
    const wrapper = render(Filters, { props: {
      dates: { start: '2026-03-01T00:00:00Z', end: '2026-03-31T00:00:00Z', maximum: 7 * 86400000 },
      report: true,
      environments: [{ value: 'env', label: 'Env' }], assets: [{ value: 'a', label: 'Asset' }],
      services: [{ value: 'api', label: 'API' }, { value: 'worker', label: 'Worker' }],
      selectedEnvironments: ['env'], selectedAssets: ['a'], selectedServices: ['api'], navigate
    } });
    await wrapper.find('form').trigger('submit');
    const params = new URLSearchParams(navigate.mock.lastCall[0].slice(1));
    expect(params.get('from')).toBe('2026-03-24T00:00');
    expect(params.get('to')).toBe('2026-03-31T00:00');
    expect(params.getAll('services')).toEqual(['a=api']);
    await wrapper.find('#assetList input').setValue(false);
    expect(new URLSearchParams(navigate.mock.lastCall[0].slice(1)).has('services')).toBe(false);
    await wrapper.find('#envList input').trigger('change');
    expect(new URLSearchParams(navigate.mock.lastCall[0].slice(1)).has('services')).toBe(false);
    await wrapper.find('#btnReset').trigger('click');
    expect(navigate).toHaveBeenLastCalledWith(window.location.pathname);
  });

  it('uses native dates explicitly when easepick is unavailable', async () => {
    const warning = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const wrapper = render(DateRange, { props: { start: '2026-10-01T00:00:00Z', end: '2026-10-03T00:00:00Z', maximum: 0 } });
    await nextTick();
    expect(wrapper.find('[name=to]').element.value).toBe('2026-10-02');
    expect(wrapper.find('[name=from]').attributes('type')).toBe('date');
    expect(warning).toHaveBeenCalled();
  });

  it('updates hidden UTC dates from easepick, clamps its range and destroys the picker', async () => {
    vi.useFakeTimers();
    let select;
    const destroy = vi.fn();
    const setDateRange = vi.fn();
    vi.stubGlobal('matchMedia', () => ({ matches: false }));
    vi.stubGlobal('easepick', { create: function (options) {
      this.destroy = destroy;
      this.setDateRange = setDateRange;
      options.setup({ on: (name, handler) => { if (name === 'select') select = handler; } });
    } });
    const wrapper = render(DateRange, { props: {
      start: '2026-10-01T00:00:00Z', end: '2026-10-03T00:00:00Z', maximum: 7 * 86400000
    } });
    select({ detail: { start: new Date(2026, 9, 1), end: new Date(2026, 9, 10) } });
    await nextTick();
    expect(wrapper.find('[name=from]').element.value).toBe('2026-10-04');
    expect(wrapper.find('[name=to]').element.value).toBe('2026-10-10');
    vi.runAllTimers();
    expect(setDateRange).toHaveBeenCalledWith(new Date(2026, 9, 4), new Date(2026, 9, 10));
    wrapper.unmount();
    expect(destroy).toHaveBeenCalledOnce();
  });

  it('persists theme state', async () => {
    document.documentElement.setAttribute('data-theme', 'light');
    const wrapper = render(ThemeToggle);
    await wrapper.find('button').trigger('click');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem('wombat.theme')).toBe('dark');
  });

  it('repaints charts on theme changes and destroys them on unmount', async () => {
    const destroy = vi.fn();
    const Chart = vi.fn(function () { this.destroy = destroy; });
    Chart.defaults = { font: {}, plugins: { legend: { labels: {} } } };
    vi.stubGlobal('Chart', Chart);
    const wrapper = render(ChartCard, { props: { metric: 'shares', title: 'Shares', services: [] } });
    document.documentElement.setAttribute('data-theme', 'light');
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(Chart).toHaveBeenCalledTimes(2);
    wrapper.unmount();
    expect(destroy).toHaveBeenCalledTimes(2);
  });

  it('switches timeline metric and persists bar mode', async () => {
    const Chart = vi.fn(function () { this.destroy = vi.fn(); });
    Chart.defaults = { font: {}, plugins: { legend: { labels: {} } } };
    vi.stubGlobal('Chart', Chart);
    const wrapper = render(TimelineChart, { props: {
      timeline: { starts: [0], stepMs: 86400000, series: [
        { key: 'gwp', unit: 'kg', buckets: [{ embedded: 1, use: 2 }] },
        { key: 'pe', unit: 'MJ', buckets: [{ embedded: 2, use: 3 }] }
      ] }, title: 'Timeline', note: 'note', labels: [{ key: 'gwp', label: 'GWP' }, { key: 'pe', label: 'PE' }]
    } });
    await wrapper.find('[data-series=pe]').trigger('click');
    expect(wrapper.find('[data-series=pe]').attributes('aria-pressed')).toBe('true');
    await wrapper.find('[data-mode=bar]').trigger('click');
    expect(localStorage.getItem('wombat.timeline.mode')).toBe('bar');
    expect(Chart.mock.lastCall[1].type).toBe('bar');
  });

  it('creates detail charts lazily, reuses them on reopen, and cleans them up', async () => {
    const destroy = vi.fn();
    const resize = vi.fn();
    const Chart = vi.fn(function () { Object.assign(this, { destroy, resize, reset: vi.fn(), update: vi.fn() }); });
    Chart.defaults = { font: {}, plugins: { legend: { labels: {} } } };
    vi.stubGlobal('Chart', Chart);
    const summary = document.createElement('tr');
    summary.innerHTML = '<td><span class="row-chevron"></span></td><td>Server-formatted value</td>';
    const wrapper = render(ServiceRow, { props: {
      summary, index: '1', service: { name: 'API', gwp: { embedded: 1, use: 2 }, pe: { embedded: 2, use: 3 }, adp: { embedded: 3, use: 4 } }
    } });
    expect(Chart).not.toHaveBeenCalled();
    expect(wrapper.find('.service-summary').text()).toContain('Server-formatted value');
    await wrapper.find('.service-summary').trigger('click');
    expect(Chart).toHaveBeenCalledTimes(3);
    await wrapper.find('.service-summary').trigger('keydown', { key: 'Enter' });
    await wrapper.find('.service-summary').trigger('click');
    expect(resize).toHaveBeenCalledTimes(3);
    wrapper.unmount();
    expect(destroy).toHaveBeenCalledTimes(3);
  });
});
