/* ── Theme: apply before paint to prevent FOUC ── */
(function() {
  var stored = null;
  try { stored = localStorage.getItem('wombat.theme'); } catch (e) { }
  document.documentElement.setAttribute('data-theme', stored || 'light');
})();

function toggleTheme() {
  var next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', next);
  try { localStorage.setItem('wombat.theme', next); } catch (e) { }
}

document.addEventListener('DOMContentLoaded', function() {
  var btn = document.getElementById('themeToggle');
  if (btn) btn.addEventListener('click', toggleTheme);
});

/* ── Date conversions ──
   The wire contract is UTC (`from`/`to` query params, `data-*-utc` attributes) and the range is
   picked by whole days: it always runs from midnight UTC to midnight UTC. Days travel through the
   UI as "YYYY-MM-DD" strings so the browser's timezone can never shift one, and the `to` bound
   sent to the backend is exclusive — midnight UTC of the day *after* the last day picked — so the
   last day is covered in full. These helpers are the single place that crosses that boundary. ── */
var DAY_MS = 86400000;

/* Dates are rendered in English rather than the reader's locale: every other word on these pages is
   English, and the server pins Locale.US for the numbers it formats (TemplateFormatters), so a month
   name following the browser would be the one thing on the page that changed language. Shared by the
   header's range label and the histogram's axis. */
var DATE_LOCALE = 'en-US';

function pad2(n) { return (n < 10 ? '0' : '') + n; }

/* A JS Date -> the "YYYY-MM-DD" UTC day it falls in. */
function dateToUtcDay(d) {
  return d.getUTCFullYear() + '-' + pad2(d.getUTCMonth() + 1) + '-' + pad2(d.getUTCDate());
}

/* "YYYY-MM-DD" -> midnight UTC of that day. */
function utcDayToDate(day) { return new Date(day + 'T00:00:00Z'); }

/* "YYYY-MM-DD" moved by a whole number of days, still UTC. */
function shiftUtcDay(day, days) {
  return dateToUtcDay(new Date(utcDayToDate(day).getTime() + days * DAY_MS));
}

/* "YYYY-MM-DD" moved by a whole number of calendar months, still UTC. A day the target month does
   not have (31 May shifted back three months) lands on that month's last day rather than spilling
   over into the next one, which is what "three months earlier" means to a reader. */
function shiftUtcMonth(day, months) {
  var d = utcDayToDate(day);
  var firstOfTarget = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + months, 1));
  var daysInTarget = new Date(Date.UTC(firstOfTarget.getUTCFullYear(), firstOfTarget.getUTCMonth() + 1, 0)).getUTCDate();
  firstOfTarget.setUTCDate(Math.min(d.getUTCDate(), daysInTarget));
  return dateToUtcDay(firstOfTarget);
}

/* Whole days between two UTC days (UTC has no DST, so the division is exact). */
function utcDayDiff(fromDay, toDay) {
  return Math.round((utcDayToDate(toDay).getTime() - utcDayToDate(fromDay).getTime()) / DAY_MS);
}

/* A UTC ISO instant -> the first day of the range it opens. */
function utcIsoToStartDay(iso) { return iso ? dateToUtcDay(new Date(iso)) : ''; }

/* A UTC ISO instant -> the last day the range covers. The bound is exclusive, so step back an
   instant before reading the day off it. */
function utcIsoToEndDay(iso) { return iso ? dateToUtcDay(new Date(new Date(iso).getTime() - 1)) : ''; }

/* "YYYY-MM-DD" <-> the Date easepick expects. easepick reads calendar days off local Date fields,
   so a UTC day is handed to it as the same wall-clock day at local midnight and read straight back
   the same way — only the day ever matters, never the instant it stands for. */
function utcDayToPickerDate(day) {
  var parts = day.split('-');
  return new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
}

function pickerDateToUtcDay(d) {
  return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate());
}

/* The configured maximum span in whole days: a range of N days covers exactly N × 24h of UTC. */
function maxSpanDaysOf(picker) {
  var ms = parseInt(picker.dataset.maxSpanMs, 10) || 0;
  return ms ? Math.max(1, Math.floor(ms / DAY_MS)) : 0;
}

/* The start day clamped so the inclusive range [start, end] never exceeds maxSpanDays. */
function clampStartDay(startDay, endDay, maxSpanDays) {
  if (!startDay || !endDay || !maxSpanDays) return startDay;
  return utcDayDiff(startDay, endDay) + 1 > maxSpanDays ? shiftUtcDay(endDay, -(maxSpanDays - 1)) : startDay;
}

/* ── UTC day range label ── */
function formatUtcDayRange(startIso, endIso) {
  var opts = { year: 'numeric', month: 'short', day: 'numeric', timeZone: 'UTC' };
  var fmt = function(day) { return day ? utcDayToDate(day).toLocaleDateString(DATE_LOCALE, opts) : '—'; };
  return fmt(utcIsoToStartDay(startIso)) + ' → ' + fmt(utcIsoToEndDay(endIso)) + ' (UTC)';
}

/* ── Shared date-range submission ──
   Reads the from/to day inputs, clamps the span to the picker's max, and appends the UTC
   `from`/`to` query params — midnight UTC to midnight UTC, the `to` bound landing on the day after
   the last day picked. Used by every screen (global report and error page) so the range submitted
   is always whole UTC days and always clamped, regardless of how it was picked. ── */
function appendDateParams(params) {
  var inFrom = document.getElementById('inputFrom');
  var inTo   = document.getElementById('inputTo');
  if (!inFrom || !inTo) return params;

  var toDay   = inTo.value;
  var picker  = document.getElementById('dateRangePicker');
  var fromDay = clampStartDay(inFrom.value, toDay, picker ? maxSpanDaysOf(picker) : 0);

  if (fromDay) params.set('from', fromDay + 'T00:00');
  if (toDay)   params.set('to',   shiftUtcDay(toDay, 1) + 'T00:00');
  return params;
}

/* ── easepick range picker ── */
function buildDateRangePicker(picker, inFrom, inTo) {
  var fromDay = utcIsoToStartDay(picker.dataset.fromUtc);
  var toDay   = utcIsoToEndDay(picker.dataset.toUtc);
  var maxSpanDays = maxSpanDaysOf(picker);

  var today = dateToUtcDay(new Date());
  function preset(firstDay) { return [utcDayToPickerDate(firstDay), utcDayToPickerDate(today)]; }

  // A range covers whole UTC days up to and including today, so the day after today is its exclusive
  // end — and a "last N months" shortcut opens exactly N calendar months before *that*, the same
  // arithmetic the server does for the default range (UIController.DEFAULT_RANGE_MONTHS). Counting
  // back from today instead would leave the shortcut a day wider than the range it is meant to match.
  var endExclusive = shiftUtcDay(today, 1);
  function monthsBack(months) { return preset(shiftUtcMonth(endExclusive, -months)); }

  // The two short ones are what the histogram charts by the hour rather than by the day, so they stay
  // within reach of the long ones the report opens on.
  var presets = {
    'Today':         preset(today),
    'Last 7 days':   preset(shiftUtcDay(today, -6)),
    'Last 3 months': monthsBack(3),
    'Last 6 months': monthsBack(6),
    'Last year':     monthsBack(12)
  };

  var ep = new easepick.create({
    element: picker,
    css: [
      '/vendor/easepick.css',
      '/easepick-theme.css'
    ],
    zIndex: 10,
    grid: window.matchMedia('(max-width: 720px)').matches ? 1 : 2,
    calendars: window.matchMedia('(max-width: 720px)').matches ? 1 : 2,
    format: 'YYYY-MM-DD',
    plugins: ['RangePlugin', 'PresetPlugin', 'LockPlugin'],
    RangePlugin: {
      tooltip: true,
      startDate: fromDay ? utcDayToPickerDate(fromDay) : undefined,
      endDate: toDay ? utcDayToPickerDate(toDay) : undefined,
      repick: true,
      delimiter: ' → '
    },
    PresetPlugin: { customPreset: presets, position: 'bottom' },
    LockPlugin: {
      filter: function(date, picked) {
        if (!maxSpanDays || !picked || picked.length !== 1) return false;
        var anchor = pickerDateToUtcDay(picked[0].toJSDate());
        return Math.abs(utcDayDiff(anchor, pickerDateToUtcDay(date.toJSDate()))) > maxSpanDays - 1;
      }
    },
    setup: function(p) {
      // Clamp a range to maxSpan and mirror it into the hidden inputs; returns the (possibly clamped) days.
      function commit(start, end) {
        var endDay = end ? pickerDateToUtcDay(end) : '';
        var startDay = clampStartDay(start ? pickerDateToUtcDay(start) : '', endDay, maxSpanDays);
        if (startDay) inFrom.value = startDay;
        if (endDay)   inTo.value   = endDay;
        return [startDay, endDay];
      }

      var clamping = false;
      // Presets ("Last 7 days", …) go through the same event: easepick applies them with
      // setDateRange and then triggers 'select' with the resulting range.
      p.on('select', function(e) {
        var start = e.detail.start;
        var end = e.detail.end;
        if (clamping) { clamping = false; commit(start, end); return; }
        var clamped = commit(start, end);
        // If the range was clamped, push the corrected range back into the picker's display.
        if (start && clamped[0] && clamped[0] !== pickerDateToUtcDay(start)) {
          clamping = true;
          setTimeout(function() { ep.setDateRange(utcDayToPickerDate(clamped[0]), utcDayToPickerDate(clamped[1])); }, 0);
        }
      });
    }
  });
}

/* ── Native fallback ──
   When the easepick bundle is unavailable (e.g. its CDN is blocked), turn the two hidden inputs
   into visible date fields so the range is still editable instead of being silently pinned to the
   server default. A native date input speaks the same "YYYY-MM-DD" day values as the picker, and
   reuses the .filter-bar input[type=date] CSS. ── */
function enableNativeDateFallback(picker, inFrom, inTo) {
  if (picker) picker.style.display = 'none';
  [inFrom, inTo].forEach(function(el) {
    el.type = 'date';
    el.classList.add('date-range-native');
  });
}

/* ── Date range picker + header meta ──
   Used by every screen that exposes #dateRangePicker (the global report and the error page),
   so the date range control stays identical across them. ── */
function initDateRangePicker() {
  var picker = document.getElementById('dateRangePicker');
  var inFrom = document.getElementById('inputFrom');
  var inTo   = document.getElementById('inputTo');

  if (picker && inFrom && inTo) {
    // Always seed the hidden inputs from the server-provided UTC range first, so a submit is
    // correct even if easepick never initialises. Every later interaction only overwrites these.
    inFrom.value = utcIsoToStartDay(picker.dataset.fromUtc);
    inTo.value   = utcIsoToEndDay(picker.dataset.toUtc);

    if (window.easepick) buildDateRangePicker(picker, inFrom, inTo);
    else                 enableNativeDateFallback(picker, inFrom, inTo);
  }

  var meta = document.getElementById('headerMeta');
  if (meta) meta.textContent = formatUtcDayRange(meta.dataset.start, meta.dataset.end);
}

document.addEventListener('DOMContentLoaded', initDateRangePicker);
