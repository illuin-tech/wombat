export const DAY_MS = 86400000;
export const DATE_LOCALE = 'en-US';
const pad2 = value => String(value).padStart(2, '0');

export function dateToUtcDay(date) {
  return `${date.getUTCFullYear()}-${pad2(date.getUTCMonth() + 1)}-${pad2(date.getUTCDate())}`;
}

export function utcDayToDate(day) {
  return new Date(`${day}T00:00:00Z`);
}

export function shiftUtcDay(day, days) {
  return dateToUtcDay(new Date(utcDayToDate(day).getTime() + days * DAY_MS));
}

export function shiftUtcMonth(day, months) {
  const date = utcDayToDate(day);
  const target = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + months, 1));
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(date.getUTCDate(), lastDay));
  return dateToUtcDay(target);
}

export function utcDayDiff(from, to) {
  return Math.round((utcDayToDate(to).getTime() - utcDayToDate(from).getTime()) / DAY_MS);
}

export function utcIsoToStartDay(iso) {
  return iso ? dateToUtcDay(new Date(iso)) : '';
}

export function utcIsoToEndDay(iso) {
  return iso ? dateToUtcDay(new Date(new Date(iso).getTime() - 1)) : '';
}

export function utcDayToPickerDate(day) {
  const [year, month, date] = day.split('-').map(Number);
  return new Date(year, month - 1, date);
}

export function pickerDateToUtcDay(date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

export function maxSpanDaysOf(picker) {
  const millis = Number.parseInt(picker.dataset.maxSpanMs, 10) || 0;
  return millis ? Math.max(1, Math.floor(millis / DAY_MS)) : 0;
}

export function clampStartDay(from, to, maximum) {
  if (!from || !to || !maximum) return from;
  return utcDayDiff(from, to) + 1 > maximum ? shiftUtcDay(to, -(maximum - 1)) : from;
}

export function formatUtcDayRange(start, end) {
  const formatter = new Intl.DateTimeFormat(DATE_LOCALE, { year: 'numeric', month: 'short', day: 'numeric', timeZone: 'UTC' });
  const format = day => day ? formatter.format(utcDayToDate(day)) : '\u2014';
  return `${format(utcIsoToStartDay(start))} \u2192 ${format(utcIsoToEndDay(end))} (UTC)`;
}
