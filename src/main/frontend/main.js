import { createApp } from 'vue';
import Filters from './components/Filters.vue';
import ThemeToggle from './components/ThemeToggle.vue';
import ChartCard from './components/ChartCard.vue';
import TimelineChart from './components/TimelineChart.vue';
import Modal from './components/Modal.vue';
import ServiceRow from './components/ServiceRow.vue';
import { formatUtcDayRange } from './utils/utc-date.js';

const apps = [];
function mount(element, component, props, tag = 'div') {
  const host = document.createElement(tag);
  element.replaceWith(host);
  const app = createApp(component, props);
  app.mount(host);
  apps.push(app);
  return host;
}

document.querySelectorAll('[data-vue-theme]').forEach(element => {
  // Avoid a wrapper in the flex header so its auto margins retain their existing behavior.
  const host = mount(element, ThemeToggle);
  host.setAttribute('data-vue-theme-root', '');
  host.style.display = 'contents';
});
document.querySelectorAll('.header-meta[data-start]').forEach(element => {
  element.textContent = formatUtcDayRange(element.dataset.start, element.dataset.end);
});
document.querySelectorAll('[data-vue-filters]').forEach(element => {
  const entries = name => [...element.content.querySelectorAll(`select[name="${name}"] option`)];
  const options = name => entries(name).map(option => ({
    value: option.value,
    label: option.textContent.trim()
  }));
  const selected = name => entries(name).filter(option => option.selected).map(option => option.value);
  mount(element, Filters, {
    dates: { start: element.dataset.fromUtc, end: element.dataset.toUtc, maximum: Number(element.dataset.maxSpanMs) },
    report: element.dataset.vueFilters === 'report',
    environments: options('environment'), assets: options('assets'), services: options('containers'),
    selectedEnvironments: selected('environment'), selectedAssets: selected('assets'), selectedServices: selected('containers')
  });
});
document.querySelectorAll('[data-vue-modal]').forEach(element => {
  const id = element.id;
  const trigger = document.querySelector(`[data-modal-target="${id}"]`);
  if (!trigger) throw new Error(`Missing trigger for modal ${id}`);
  mount(element, Modal, {
    id, trigger, title: element.dataset.title,
    regime: element.dataset.regime || '',
    nodes: [...element.content.childNodes]
  });
});

const payload = document.getElementById('impact-data');
let observer;
if (payload) {
  const data = JSON.parse(payload.textContent);
  document.querySelectorAll('[data-vue-chart]').forEach(element => {
    const host = mount(element, ChartCard, {
      metric: element.dataset.vueChart, color: element.dataset.color || '',
      title: element.dataset.title, services: data.services
    });
    host.style.display = 'contents';
  });
  document.querySelectorAll('[data-vue-timeline]').forEach(element => {
    const host = mount(element, TimelineChart, {
      timeline: data.timeline, title: element.dataset.title,
      note: element.dataset.note,
      labels: data.timeline.series.map(series => ({ key: series.key, label: series.label }))
    });
    host.style.display = 'contents';
  });
  document.querySelectorAll('[data-vue-service]').forEach(element => {
    mount(element, ServiceRow, {
      service: data.services[Number(element.dataset.vueService)],
      summary: element.querySelector('.service-summary'),
      index: element.querySelector('.service-summary').dataset.idx
    }, 'tbody');
  });
  const overview = document.querySelector('.overview-row');
  if (overview) {
    const shares = overview.querySelector('.chart-card');
    const panel = overview.querySelector('.kpi-card');
    observer = new ResizeObserver(() => { panel.style.height = `${shares.offsetHeight}px`; });
    observer.observe(shares);
  }
}
function dispose(event) {
  if (event.persisted) return;
  observer?.disconnect();
  apps.forEach(app => app.unmount());
  window.removeEventListener('pagehide', dispose);
}
window.addEventListener('pagehide', dispose);
