import { afterEach, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';

afterEach(() => {
  window.dispatchEvent(new Event('pagehide'));
  document.body.replaceChildren();
  vi.unstubAllGlobals();
  vi.resetModules();
});

it('mounts precompiled islands without compiling server text and disposes every chart on pagehide', async () => {
  const destroy = vi.fn();
  const Chart = vi.fn(function () { this.destroy = destroy; });
  Chart.defaults = { font: {} };
  vi.stubGlobal('Chart', Chart);
  const disconnect = vi.fn();
  vi.stubGlobal('ResizeObserver', class {
    observe() {}
    disconnect() { disconnect(); }
  });
  document.body.innerHTML = `
    <header><span id="server-name">{{ globalThis.unsafe = true }}</span><button data-vue-theme></button></header>
    <div class="overview-row">
      <div data-vue-chart="shares" data-title="GWP shares"></div>
      <div class="kpi-card"><button data-modal-target="asset">Open</button></div>
    </div>
    <template id="asset" data-vue-modal data-title="Asset {{ globalThis.unsafe = true }}" data-regime="Modeled">
      <p>Already escaped server data</p>
    </template>
    <script id="impact-data" type="application/json">{"services":[],"timeline":{}}</script>`;
  await import('../../main/frontend/main.js');
  await nextTick();
  expect(Chart).toHaveBeenCalledTimes(1);
  expect(document.querySelector('#themeToggle')).not.toBeNull();
  expect(document.querySelector('#server-name').textContent).toBe('{{ globalThis.unsafe = true }}');
  document.querySelector('[data-modal-target]').click();
  await nextTick();
  expect(document.querySelector('.asset-modal-overlay').style.display).not.toBe('none');
  expect(document.querySelector('.asset-modal-title').textContent).toContain('{{ globalThis.unsafe');
  expect(document.querySelector('.asset-modal-body p').textContent).toBe('Already escaped server data');
  expect(globalThis.unsafe).toBeUndefined();
  const persisted = new Event('pagehide');
  Object.defineProperty(persisted, 'persisted', { value: true });
  window.dispatchEvent(persisted);
  expect(destroy).not.toHaveBeenCalled();
  window.dispatchEvent(new Event('pagehide'));
  expect(destroy).toHaveBeenCalledTimes(1);
  expect(disconnect).toHaveBeenCalledTimes(1);
  expect(document.body.classList.contains('modal-open')).toBe(false);
});

it('builds filters, timeline and service details from data-only mount points', async () => {
  vi.spyOn(console, 'warn').mockImplementation(() => {});
  const destroy = vi.fn();
  const Chart = vi.fn(function () {
    Object.assign(this, { destroy, resize: vi.fn(), reset: vi.fn(), update: vi.fn() });
  });
  Chart.defaults = { font: {}, plugins: { legend: { labels: {} } } };
  vi.stubGlobal('Chart', Chart);
  const metric = { embedded: 1, use: 2 };
  document.body.innerHTML = `
    <template data-vue-filters="report" data-from-utc="2026-10-01T00:00:00Z"
              data-to-utc="2026-10-03T00:00:00Z" data-max-span-ms="86400000">
      <select multiple name="environment">
        <option value="test" selected>&lt;Test&gt; {{ globalThis.unsafe = true }}</option>
      </select>
      <select multiple name="assets">
        <option value="cluster" selected>Cluster — cluster / default</option>
        <option value="other">Other</option>
      </select>
      <select multiple name="containers">
        <option value="api" selected>API</option>
        <option value="worker">Worker</option>
      </select>
    </template>
    <div data-vue-timeline data-title="Impact over time — day" data-note="Two days of activity"></div>
    <table><tbody data-vue-service="0">
      <tr class="service-summary" data-idx="1">
        <td><span class="row-chevron"></span></td><td>Server-formatted 1,234.50</td>
      </tr>
    </tbody></table>
    <script id="impact-data" type="application/json">${JSON.stringify({
      services: [{ name: 'API', gwp: metric, pe: metric, adp: metric }],
      timeline: { starts: [0], stepMs: 86400000, series: [
        { key: 'gwp', label: 'GWP', unit: 'kg', buckets: [metric] },
        { key: 'pe', label: 'PE', unit: 'MJ', buckets: [metric] }
      ] }
    })}</script>`;
  const summaryCell = document.querySelector('.service-summary td:last-child');
  await import('../../main/frontend/main.js');
  await nextTick();
  expect(document.querySelectorAll('#filterForm')).toHaveLength(1);
  expect(document.querySelector('#envList input').value).toBe('test');
  expect(document.querySelector('#envList input').checked).toBe(true);
  expect(document.querySelector('#envList label').textContent).toContain('<Test> {{ globalThis.unsafe = true }}');
  expect(document.querySelector('#assetList input[value=other]').checked).toBe(false);
  expect(document.querySelector('#serviceList input[value=api]').checked).toBe(true);
  expect(document.querySelector('#serviceList input[value=worker]').checked).toBe(false);
  expect(document.querySelector('#dateRangePicker').dataset.maxSpanMs).toBe('86400000');
  expect(document.querySelector('[name=from]').value).toBe('2026-10-01');
  expect(document.querySelector('[name=to]').value).toBe('2026-10-02');
  expect(document.querySelector('[data-series=pe]').textContent).toBe('PE');
  expect(document.querySelector('.chart-note').textContent).toBe('Two days of activity');
  expect(document.querySelectorAll('.service-detail')).toHaveLength(1);
  expect(document.querySelector('.service-summary td:last-child')).toBe(summaryCell);
  expect(Chart).toHaveBeenCalledTimes(1);
  document.querySelector('.service-summary').click();
  await nextTick();
  expect(document.querySelector('.service-summary').getAttribute('aria-expanded')).toBe('true');
  expect(document.querySelectorAll('.detail-chart-card canvas')).toHaveLength(3);
  expect(Chart).toHaveBeenCalledTimes(4);
  expect(globalThis.unsafe).toBeUndefined();
  window.dispatchEvent(new Event('pagehide'));
  expect(destroy).toHaveBeenCalledTimes(4);
});

it('mounts the dates-only filter without report pickers', async () => {
  vi.spyOn(console, 'warn').mockImplementation(() => {});
  document.body.innerHTML = `
    <template data-vue-filters="dates" data-from-utc="2026-10-01T00:00:00Z"
              data-to-utc="2026-10-03T00:00:00Z" data-max-span-ms="0"></template>`;
  await import('../../main/frontend/main.js');
  await nextTick();
  expect(document.querySelectorAll('#filterForm')).toHaveLength(1);
  expect(document.querySelector('[name=from]').value).toBe('2026-10-01');
  expect(document.querySelectorAll('.container-picker')).toHaveLength(0);
  expect(document.querySelector('.btn-apply').textContent).toBe('Apply');
  expect(document.querySelector('#btnReset')).toBeNull();
});
