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
   The wire contract is always UTC (`from`/`to` query params, `data-*-utc` attributes).
   The UI works in the browser's local wall-clock. These three helpers are the single place
   that crosses that boundary, so every screen converts identically. ── */
function pad2(n) { return (n < 10 ? '0' : '') + n; }

/* A JS Date -> "YYYY-MM-DDTHH:mm" in the browser's local wall-clock (datetime-local format). */
function dateToLocalInput(d) {
  return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate())
    + 'T' + pad2(d.getHours()) + ':' + pad2(d.getMinutes());
}

/* A UTC ISO instant string -> the local wall-clock value a datetime-local input expects. */
function utcIsoToLocalInput(iso) {
  return iso ? dateToLocalInput(new Date(iso)) : '';
}

/* A local wall-clock value ("YYYY-MM-DDTHH:mm") -> the same instant expressed in UTC for the backend. */
function localInputToUtc(local) {
  if (!local) return '';
  return new Date(local).toISOString().slice(0, 16);
}

/* ── Local time range label ── */
function formatLocalRange(startIso, endIso) {
  var opts = { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' };
  var fmt = function(iso) { return iso ? new Date(iso).toLocaleString(navigator.language, opts) : '—'; };
  return fmt(startIso) + ' → ' + fmt(endIso);
}

/* ── Shared date-range submission ──
   Reads the from/to inputs, clamps the span to the picker's max, and appends the UTC
   `from`/`to` query params. Used by every screen (global report and error page) so the
   range submitted is always UTC and always clamped, regardless of how it was picked. ── */
function appendDateParams(params) {
  var inFrom = document.getElementById('inputFrom');
  var inTo   = document.getElementById('inputTo');
  if (!inFrom || !inTo) return params;

  var from = inFrom.value;
  var to   = inTo.value;
  var picker = document.getElementById('dateRangePicker');
  var maxSpanMs = picker ? (parseInt(picker.dataset.maxSpanMs, 10) || 0) : 0;

  if (from && to && maxSpanMs) {
    var fromMs = new Date(from).getTime();
    var toMs   = new Date(to).getTime();
    if (toMs - fromMs > maxSpanMs) from = dateToLocalInput(new Date(toMs - maxSpanMs));
  }
  if (from) params.set('from', localInputToUtc(from));
  if (to)   params.set('to',   localInputToUtc(to));
  return params;
}

/* ── easepick range picker ── */
function buildDateRangePicker(picker, inFrom, inTo) {
  function isoToDate(iso) { return iso ? new Date(iso) : null; }

  var fromDate = isoToDate(picker.dataset.fromUtc);
  var toDate   = isoToDate(picker.dataset.toUtc);
  var maxSpanMs = parseInt(picker.dataset.maxSpanMs, 10) || 0;

  function nowMinus(hours) { return new Date(Date.now() - hours * 3600000); }
  var presets = {
    'Last hour':    [nowMinus(1),   new Date()],
    'Last 24h':     [nowMinus(24),  new Date()],
    'Last 7 days':  [nowMinus(168), new Date()],
    'Last 30 days': [nowMinus(720), new Date()]
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
    format: 'YYYY-MM-DD HH:mm',
    plugins: ['RangePlugin', 'TimePlugin', 'PresetPlugin', 'LockPlugin'],
    RangePlugin: {
      tooltip: true,
      startDate: fromDate || undefined,
      endDate: toDate || undefined,
      repick: true,
      delimiter: ' → '
    },
    TimePlugin: { format: 'HH:mm', stepMinutes: 5 },
    PresetPlugin: { customPreset: presets, position: 'bottom' },
    LockPlugin: {
      filter: function(date, picked) {
        if (!maxSpanMs || !picked || picked.length !== 1) return false;
        var anchorMs = picked[0].toJSDate().getTime();
        var dMs = date.toJSDate().getTime();
        return Math.abs(dMs - anchorMs) > maxSpanMs;
      }
    },
    setup: function(p) {
      // Clamp a range to maxSpan and mirror it into the hidden inputs; returns the (possibly clamped) range.
      function commit(start, end) {
        if (start && end && maxSpanMs && (end.getTime() - start.getTime()) > maxSpanMs)
          start = new Date(end.getTime() - maxSpanMs);
        if (start) inFrom.value = dateToLocalInput(start);
        if (end)   inTo.value   = dateToLocalInput(end);
        return [start, end];
      }

      var clamping = false;
      p.on('select', function(e) {
        var start = e.detail.start;
        var end = e.detail.end;
        if (clamping) { clamping = false; commit(start, end); return; }
        var clamped = commit(start, end);
        // If the range was clamped, push the corrected range back into the picker's display.
        if (start && clamped[0] && clamped[0].getTime() !== start.getTime()) {
          clamping = true;
          setTimeout(function() { ep.setDateRange(clamped[0], clamped[1]); }, 0);
        }
      });

      // Presets ("Last 7 days", …) are relative to now, but easepick's TimePlugin re-pins the
      // time-of-day to the previously picked hours. Let easepick apply the preset, then on the
      // next tick overwrite both the display and the hidden inputs with the exact rolling window.
      p.on('view', function(e) {
        if (e.detail.view !== 'PresetPluginButton' || !e.detail.target) return;
        var btn = e.detail.target;
        btn.addEventListener('click', function() {
          var start = new Date(Number(btn.dataset.start));
          var end   = new Date(Number(btn.dataset.end));
          setTimeout(function() {
            var clamped = commit(start, end);
            var s = clamped[0], e2 = clamped[1];
            ep.setDateRange(s, e2);
            // setDateRange only sets the calendar day; the TimePlugin keeps its own hours, so
            // sync those too or the display keeps the previously picked time-of-day.
            if (ep.setStartTime) ep.setStartTime(pad2(s.getHours()) + ':' + pad2(s.getMinutes()));
            if (ep.setEndTime)   ep.setEndTime(pad2(e2.getHours()) + ':' + pad2(e2.getMinutes()));
          }, 0);
        });
      });
    }
  });
}

/* ── Native fallback ──
   When the easepick bundle is unavailable (e.g. its CDN is blocked), turn the two hidden
   inputs into visible datetime-local fields so the range is still editable instead of being
   silently pinned to the server default. Reuses the .filter-bar input[type=datetime-local] CSS. ── */
function enableNativeDateFallback(picker, inFrom, inTo) {
  if (picker) picker.style.display = 'none';
  [inFrom, inTo].forEach(function(el) {
    el.type = 'datetime-local';
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
    inFrom.value = utcIsoToLocalInput(picker.dataset.fromUtc);
    inTo.value   = utcIsoToLocalInput(picker.dataset.toUtc);

    if (window.easepick) buildDateRangePicker(picker, inFrom, inTo);
    else                 enableNativeDateFallback(picker, inFrom, inTo);
  }

  var meta = document.getElementById('headerMeta');
  if (meta) meta.textContent = formatLocalRange(meta.dataset.start, meta.dataset.end);
}

document.addEventListener('DOMContentLoaded', initDateRangePicker);
