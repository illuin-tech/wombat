import { clampStartDay, maxSpanDaysOf, shiftUtcDay } from './utc-date.js';

export function dateParams(form) {
  const params = new URLSearchParams();
  const picker = form.querySelector('#dateRangePicker');
  const to = form.querySelector('[name="to"]').value;
  const from = clampStartDay(form.querySelector('[name="from"]').value, to, maxSpanDaysOf(picker));
  if (from) params.set('from', `${from}T00:00`);
  if (to) params.set('to', `${shiftUtcDay(to, 1)}T00:00`);
  return params;
}

export function serviceParams(params, assets, selected, total) {
  // Empty means all services on the existing backend, just like a fully selected list.
  const suffix = selected.length && selected.length !== total ? `=${selected.join(',')}` : '';
  assets.forEach(asset => params.append('services', asset + suffix));
  return params;
}
