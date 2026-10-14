import test from 'node:test';
import assert from 'node:assert/strict';
import {
  clampStartDay, shiftUtcDay, shiftUtcMonth, utcDayDiff, utcIsoToEndDay,
  utcDayToPickerDate, pickerDateToUtcDay, formatUtcDayRange
} from '../../main/frontend/utils/utc-date.js';
import { dateParams, serviceParams } from '../../main/frontend/utils/filter-query.js';
import { wrapLabel, timelineConfig } from '../../main/frontend/utils/chart-options.js';

test('UTC exclusive end includes the last picked day', () => {
  assert.equal(utcIsoToEndDay('2026-06-11T00:00:00Z'), '2026-06-10');
  assert.equal(shiftUtcDay('2026-06-10', 1), '2026-06-11');
});

test('month presets clamp month ends without spilling into the next month', () => {
  assert.equal(shiftUtcMonth('2026-05-31', -3), '2026-02-28');
  assert.equal(shiftUtcMonth('2024-05-31', -3), '2024-02-29');
});

test('UTC day math is independent of daylight saving', () => {
  assert.equal(utcDayDiff('2026-03-28', '2026-03-30'), 2);
  assert.equal(clampStartDay('2026-01-01', '2026-01-31', 7), '2026-01-25');
  assert.equal(clampStartDay('2026-01-01', '2026-01-31', 0), '2026-01-01');
  assert.equal(pickerDateToUtcDay(utcDayToPickerDate('2026-10-25')), '2026-10-25');
  assert.match(formatUtcDayRange('2026-06-04T00:00:00Z', '2026-06-11T00:00:00Z'), /Jun 4, 2026.*Jun 10, 2026.*UTC/);
});

test('all and empty service selection keep the backend all-services contract', () => {
  assert.equal(serviceParams(new URLSearchParams(), ['a', 'b'], ['x', 'y'], 2).toString(), 'services=a&services=b');
  assert.equal(serviceParams(new URLSearchParams(), ['a'], [], 2).toString(), 'services=a');
  assert.equal(serviceParams(new URLSearchParams(), ['a', 'b'], ['x'], 2).getAll('services').join('|'), 'a=x|b=x');
});

test('date submission uses an exclusive UTC end and applies the configured maximum', () => {
  const picker = { dataset: { maxSpanMs: String(7 * 86400000) } };
  const fields = {
    '#dateRangePicker': picker,
    '[name="from"]': { value: '2026-03-01' },
    '[name="to"]': { value: '2026-03-30' }
  };
  const form = { querySelector: selector => fields[selector] };
  assert.equal(dateParams(form).get('from'), '2026-03-24T00:00');
  assert.equal(dateParams(form).get('to'), '2026-03-31T00:00');
  picker.dataset.maxSpanMs = '0';
  assert.equal(dateParams(form).get('from'), '2026-03-01T00:00');
});

test('service names are not HTML and long chart labels wrap at word boundaries', () => {
  assert.deepEqual(wrapLabel('a long service label that wraps', 14), ['a long service', 'label that', 'wraps']);
  assert.equal(serviceParams(new URLSearchParams(), ['<asset>'], ['quoted "name"'], 2).get('services'), '<asset>=quoted "name"');
});

test('timeline preserves stacking, UTC labels, monotone lines and dense bars', () => {
  globalThis.document = { documentElement: { getAttribute: () => 'light' } };
  globalThis.getComputedStyle = () => ({ getPropertyValue: () => '' });
  const series = { key: 'gwp', unit: 'kgCO2eq', buckets: [{ embedded: 1, use: 2 }] };
  const timeline = { stepMs: 3600000, starts: [Date.parse('2026-06-01T00:00:00Z')] };
  const line = timelineConfig(timeline, series, 'line');
  assert.match(line.data.labels[0], /00:00/);
  assert.equal(line.data.datasets[0].cubicInterpolationMode, 'monotone');
  assert.equal(line.data.datasets[1].fill, '-1');
  assert.deepEqual(line.data.datasets[1].data, [2]);
  assert.equal(line.options.scales.y.stacked, true);
  assert.equal(timelineConfig({ ...timeline, starts: Array(121).fill(timeline.starts[0]) }, series, 'bar').data.datasets[0].borderWidth, 0);
});
