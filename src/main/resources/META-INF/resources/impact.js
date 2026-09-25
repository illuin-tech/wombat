/* ── Chart defaults ── */
var cssVar = function(name, fallback) {
  var v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return v || fallback;
};
var GREEN  = cssVar('--accent-green',  '#16a34a');
var ORANGE = cssVar('--accent-orange', '#ea580c');
var BLUE   = cssVar('--accent-blue',   '#2563eb');
var YELLOW = cssVar('--accent-yellow', '#ca8a04');
var PURPLE = '#7c3aed', TEAL  = '#0891b2';
var BORDER = cssVar('--border',      '#e3ede8');
var TEXT   = cssVar('--text-muted',  '#4d7060');
var palette = [GREEN, BLUE, YELLOW, TEAL, PURPLE, ORANGE, '#db2777', '#0284c7'];
Chart.defaults.color = TEXT; Chart.defaults.borderColor = BORDER;
Chart.defaults.font.family = "'DM Sans', sans-serif"; Chart.defaults.font.size = 12; Chart.defaults.font.weight = '500';

/* ── Overview charts ── */
// Wrap a long label onto several lines (word-aware) so it fits without being cut off.
var LABEL_WRAP = 18;
function wrapLabel(label, maxLen) {
  var words = String(label).split(' ');
  var lines = [];
  var current = '';
  words.forEach(function(w) {
    if (current && (current + ' ' + w).length > maxLen) { lines.push(current); current = w; }
    else { current = current ? current + ' ' + w : w; }
  });
  if (current) lines.push(current);
  return lines;
}
var maxLabelLines = serviceNames.reduce(function(m, l) { return Math.max(m, wrapLabel(l, LABEL_WRAP).length); }, 1);

// Full label shown on hover: "asset / service".
function fullServiceLabel(i) { return serviceAssets[i] + ' / ' + serviceNames[i]; }

// Full height needed to show every bar; the outer .chart-wrap is capped and scrolls vertically.
var HBAR_MAX = 320;
var hbarH = Math.max(130, serviceNames.length * (6 + maxLabelLines * 12) + 48);
document.querySelectorAll('.chart-wrap.hbar').forEach(function(el) {
  var inner = el.querySelector('.hbar-inner');
  if (inner) inner.style.height = hbarH + 'px';
  el.style.height = Math.min(hbarH, HBAR_MAX) + 'px';
});

new Chart(document.getElementById('chartSharesDonut'), {
  type: 'doughnut',
  data: {
    labels: serviceNames,
    datasets: [{ data: sharesValues, backgroundColor: palette.map(function(c) { return c + 'cc'; }), borderColor: palette, borderWidth: 1, hoverOffset: 8 }]
  },
  options: {
    responsive: true, maintainAspectRatio: false, cutout: '62%',
    plugins: {
      legend: { display: false },
      tooltip: { callbacks: {
        title: function(items) { return fullServiceLabel(items[0].dataIndex); },
        label: function(ctx) { return ' ' + (ctx.parsed * 100).toFixed(1) + '%'; }
      } }
    }
  }
});

(function() {
  var legendEl = document.getElementById('sharesLegend');
  serviceNames.forEach(function(label, i) {
    var item = document.createElement('div');
    item.className = 'shares-legend-item';
    item.title = fullServiceLabel(i);
    item.innerHTML =
      '<span class="shares-legend-dot" style="background:' + palette[i % palette.length] + '"></span>' +
      '<span class="shares-legend-label">' + label + '</span>' +
      '<span class="shares-legend-pct">' + (sharesValues[i] * 100).toFixed(1) + '%</span>';
    legendEl.appendChild(item);
  });
})();

function makeHBar(id, datasets, unit, color) {
  return new Chart(document.getElementById(id), {
    type: 'bar',
    data: { labels: serviceNames, datasets: datasets },
    options: {
      indexAxis: 'y', responsive: true, maintainAspectRatio: false,
      datasets: { bar: { categoryPercentage: 0.5, barPercentage: 0.9, maxBarThickness: 12 } },
      plugins: {
        legend: { position: 'top', labels: { boxWidth: 10, padding: 14 } },
        tooltip: { mode: 'index', callbacks: { title: function(items) { return fullServiceLabel(items[0].dataIndex); } } }
      },
      scales: {
        x: { stacked: true, grid: { color: BORDER }, title: { display: true, text: unit, color: color } },
        y: { stacked: true, grid: { color: BORDER }, ticks: { font: { size: 11, lineHeight: 1.05 }, callback: function(value) { return wrapLabel(this.getLabelForValue(value), LABEL_WRAP); } } }
      }
    }
  });
}

[{ id: 'chartGwp', emb: gwpEmbedded, use: gwpUse, color: GREEN,  unit: 'kgCO2eq' },
 { id: 'chartPe',  emb: peEmbedded,  use: peUse,  color: BLUE,   unit: 'MJ'      },
 { id: 'chartAdp', emb: adpEmbedded, use: adpUse, color: YELLOW, unit: 'kgSbeq'  }
].forEach(function(m) {
  makeHBar(m.id, [
    { label: 'Embedded', data: m.emb, backgroundColor: m.color + 'cc', borderColor: m.color, borderWidth: 1 },
    { label: 'Use',      data: m.use, backgroundColor: m.color + '33', borderColor: m.color, borderWidth: 1 }
  ], m.unit, m.color);
});

/* ── Impact histogram ──
   One column per bucket of the selected range — an hour for a short range, a day for a long one, the
   server having picked the step — stacked into the two halves of the footprint. The pills above the
   chart swap which metric the columns show and whether they are drawn as bars or as stacked areas;
   every series covers the same buckets, so the metric switch only swaps the numbers and the unit.
   Both the buckets and the labels are UTC: the range is picked in whole UTC days, and a bucket that
   shifted with the reader's timezone would not line up with the stored aggregation windows. ── */
(function() {
  var canvas = document.getElementById('chartImpactTimeline');
  if (!canvas || !timelineStarts.length || !timelineSeries.length) return;

  // The step the server bucketed on decides how a bucket is named: a daily column has no hour worth
  // printing, an hourly one is nothing without it. The tooltip carries the year the axis leaves out,
  // being the one place a bucket is named in full.
  // DATE_LOCALE (impactUtils.js) rather than the reader's: the labels stay English like the rest of
  // the page. The hour cycle is pinned too — 'h23' and not hour12:false, which renders midnight as
  // "24:00" and would put the wrong day's date beside it.
  var hourly = timelineStepMs < 86400000;
  var tickFormat = new Intl.DateTimeFormat(DATE_LOCALE, hourly
    ? { timeZone: 'UTC', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }
    : { timeZone: 'UTC', month: 'short', day: 'numeric' });
  var titleFormat = new Intl.DateTimeFormat(DATE_LOCALE, hourly
    ? { timeZone: 'UTC', year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }
    : { timeZone: 'UTC', year: 'numeric', month: 'short', day: 'numeric' });

  function tickLabel(ms)  { return tickFormat.format(new Date(ms)); }
  function titleLabel(ms) { return titleFormat.format(new Date(ms)) + ' UTC'; }

  // Impact values span orders of magnitude — between a busy bucket and an idle one, and between the
  // metrics themselves (MJ by the unit, kgSbeq by the millionth). Significant digits rather than
  // decimal places, and an exponent below the point where decimals turn into a row of zeroes.
  function amount(value) {
    if (!value) return '0';
    return Math.abs(value) < 0.001 ? value.toExponential(2) : String(Number(value.toPrecision(3)));
  }

  // Same rule, one digit shorter: an axis tick has to stay narrow enough not to crowd its title.
  function tick(value) {
    if (!value) return '0';
    return Math.abs(value) < 0.001 ? value.toExponential(1) : String(Number(value.toPrecision(3)));
  }

  // A year of days is 365 columns, a week of hours 168. They are drawn thin rather than scrolled
  // sideways: the shape of the whole range is what the chart is for, and a canvas wider than the card
  // would take the legend and the y axis out of view with it. Per-column borders only show up once
  // the columns are wide enough to carry them without turning into noise.
  var dense = timelineStarts.length > 120;

  // Every colour is read afresh on each render rather than captured at load. The theme toggle only
  // flips a data-theme attribute on the root — it repaints the page through CSS, but a canvas is
  // pixels, and one painted from the other theme's palette keeps it until the next navigation. That
  // staleness is not cosmetic here: the stroke below is a light grey, and a light grey line on the
  // dark surface is a white one.
  //
  // Each metric keeps the accent it already wears on this page — its KPI card, its per-service chart
  // — so switching the histogram lands on a colour the reader has already learnt.
  //
  // The two fills stay on that accent, the Use one lighter; 20% of it reads as a tint over the light
  // surface but sinks into the dark one, so it takes a step of its own per theme. The stroke gives up
  // the hue altogether for the palette's dim grey, which is a light grey against the light ground and
  // a dark one against the dark: an accent-coloured line is the brightest thing on the card in dark
  // mode and a hard edge in light mode, and either way it competes with the band it only has to
  // close. The fills still carry the metric's colour, so the line loses nothing by giving it up.
  var strokeWidth = 1.25;
  function palette() {
    var dark = document.documentElement.getAttribute('data-theme') === 'dark';
    return {
      metrics: {
        gwp: { color: cssVar('--accent-green',  '#16a34a'), name: 'green'  },
        pe:  { color: cssVar('--accent-blue',   '#2563eb'), name: 'blue'   },
        adp: { color: cssVar('--accent-yellow', '#ca8a04'), name: 'yellow' }
      },
      stroke: cssVar('--text-dim', dark ? '#6b8478' : '#9ab4a6'),
      border: cssVar('--border', dark ? '#2a3a34' : '#e3ede8'),
      text: cssVar('--text-muted', dark ? '#a3bbb0' : '#4d7060'),
      useAlpha: dark ? '80' : '33'
    };
  }

  /** An unknown metric falls back to the first accent rather than inventing a hue. */
  function accentOf(series, colours) { return colours.metrics[series.key] || colours.metrics.gwp; }

  // Bars and areas carry the same two datasets and the same stack; only how they are drawn differs.
  // An area fills down to the dataset below it rather than to the axis, which is what keeps the two
  // halves reading as a stack once the columns become a continuous line.
  function datasetsOf(series, mode, colours) {
    var color = accentOf(series, colours).color;
    function dataset(index, label, data, alpha) {
      var set = { label: label, data: data, backgroundColor: color + alpha, borderColor: color };
      if (mode === 'line') {
        set.borderColor = colours.stroke;
        set.borderWidth = strokeWidth;
        set.fill = index === 0 ? 'origin' : '-1';
        set.pointRadius = 0;
        set.pointHoverRadius = 3;
        // Monotone rather than a plain tension: these are quantities of impact, which cannot go
        // negative, and a spline fitted through a spike overshoots below its neighbours — drawing a
        // dip the data never had. Monotone smooths without inventing one.
        set.cubicInterpolationMode = 'monotone';
      }
      else set.borderWidth = dense ? 0 : 1;
      return set;
    }
    return [dataset(0, 'Embedded', series.embedded, 'cc'), dataset(1, 'Use', series.use, colours.useAlpha)];
  }

  // The card's own accent — its corner bubble, the axis title, the active pill — follows the metric too.
  var card = canvas.closest('.chart-card');
  function wearAccent(series, colours) {
    var accent = accentOf(series, colours);
    if (card) {
      card.classList.remove('green', 'blue', 'yellow');
      card.classList.add(accent.name);
      Array.prototype.forEach.call(card.querySelectorAll('.chart-pill'), function(pill) {
        // A metric pill wears its own accent, so the choice is legible before it is made; a shape
        // pill wears the metric on show, since that is the colour it would be switching the shape of.
        var pillAccent = pill.dataset.series ? (colours.metrics[pill.dataset.series] || accent) : accent;
        pill.classList.remove('green', 'blue', 'yellow');
        pill.classList.add(pillAccent.name);
      });
    }
    return accent.color;
  }

  function configOf(series, mode, accentColor, colours) {
    return {
      type: mode,
      data: { labels: timelineStarts.map(tickLabel), datasets: datasetsOf(series, mode, colours) },
      options: {
        responsive: true, maintainAspectRatio: false,
        datasets: { bar: { categoryPercentage: 0.9, barPercentage: 0.95 } },
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { position: 'top', labels: { boxWidth: 10, padding: 14 } },
          tooltip: {
            callbacks: {
              title: function(items) { return titleLabel(timelineStarts[items[0].dataIndex]); },
              label: function(ctx) { return ' ' + ctx.dataset.label + ': ' + amount(ctx.parsed.y) + ' ' + series.unit; },
              footer: function(items) {
                var total = items.reduce(function(sum, item) { return sum + item.parsed.y; }, 0);
                return 'Total: ' + amount(total) + ' ' + series.unit;
              }
            }
          }
        },
        scales: {
          x: {
            stacked: true,
            grid: { display: false },
            ticks: { autoSkip: true, maxTicksLimit: 12, maxRotation: 0, font: { size: 11 } }
          },
          y: {
            stacked: true, beginAtZero: true,
            grid: { color: colours.border },
            title: { display: true, text: series.unit, color: accentColor },
            ticks: { font: { size: 11 }, callback: function(value) { return tick(value); } }
          }
        }
      }
    };
  }

  // Chart.js fixes a chart's type when it is built, and both switches rebuild the datasets anyway,
  // so the chart is recreated rather than mutated — one code path for the metric and the shape, and
  // at a few hundred points it costs less than the branching it saves.
  var MODE_KEY = 'wombat.timeline.mode';
  var mode = 'line';
  try {
    var stored = localStorage.getItem(MODE_KEY);
    if (stored === 'bar' || stored === 'line') mode = stored;
  } catch (e) { }

  var current = timelineSeries[0];
  var chart = null;
  function render() {
    var colours = palette();
    var accentColor = wearAccent(current, colours);
    // Set before construction: a chart reads the defaults it is built with, so this reaches the tick
    // and legend text of the chart about to be created and none of the ones already on the page.
    Chart.defaults.color = colours.text;
    Chart.defaults.borderColor = colours.border;
    if (chart) chart.destroy();
    chart = new Chart(canvas, configOf(current, mode, accentColor, colours));
  }

  // The toggle writes data-theme and nothing else; this is what turns that into a repainted canvas.
  new MutationObserver(render).observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme']
  });

  function markActive(group, active) {
    Array.prototype.forEach.call(group.querySelectorAll('.chart-pill'), function(pill) {
      var on = pill === active;
      pill.classList.toggle('active', on);
      pill.setAttribute('aria-pressed', on ? 'true' : 'false');
    });
  }

  var pills = document.getElementById('timelinePills');
  if (pills) pills.addEventListener('click', function(event) {
    var button = event.target.closest('.chart-pill');
    if (!button) return;

    var selected = timelineSeries.filter(function(series) { return series.key === button.dataset.series; })[0];
    if (!selected || selected === current) return;

    current = selected;
    markActive(pills, button);
    render();
  });

  var modes = document.getElementById('timelineModes');
  if (modes) {
    // The stored shape is applied before the first draw, so a reader who prefers areas never sees the
    // bars flash past on every page load.
    markActive(modes, modes.querySelector('.chart-pill[data-mode="' + mode + '"]'));
    modes.addEventListener('click', function(event) {
      var button = event.target.closest('.chart-pill');
      if (!button || !button.dataset.mode || button.dataset.mode === mode) return;

      mode = button.dataset.mode;
      try { localStorage.setItem(MODE_KEY, mode); } catch (e) { }
      markActive(modes, button);
      render();
    });
  }

  render();
})();

/* ── Asset picker (multi-select; auto-navigates so the service picker refreshes immediately) ── */
(function() {
  var picker   = document.getElementById('assetPicker');
  var trigger  = document.getElementById('assetTrigger');
  var dropdown = document.getElementById('assetDropdown');
  if (!picker) return;

  function updateCount() {
    var boxes = document.querySelectorAll('#assetList input[name="assets"]');
    document.getElementById('assetCount').textContent =
      Array.from(boxes).filter(function(b) { return b.checked; }).length + ' / ' + boxes.length + ' assets';
  }
  function toggleDropdown(open) {
    trigger.classList.toggle('open', open);
    dropdown.classList.toggle('open', open);
  }

  trigger.addEventListener('click', function(e) { e.stopPropagation(); toggleDropdown(!dropdown.classList.contains('open')); });
  document.addEventListener('click', function(e) { if (!picker.contains(e.target)) toggleDropdown(false); });
  document.addEventListener('keydown', function(e) { if (e.key === 'Escape') toggleDropdown(false); });

  // Changing the asset selection navigates immediately (dropping the service filter) so the
  // services of the new selection are all selected and shown in the picker without clicking Apply.
  document.querySelectorAll('#assetList input[name="assets"]').forEach(function(b) {
    b.addEventListener('change', function() {
      updateCount();
      var p = appendDateParams(new URLSearchParams());
      var env = document.querySelector('#envList input[name="environment"]:checked');
      if (env) p.set('environment', env.value);
      // Each selected asset with no service list => all services of that asset (selection reset).
      document.querySelectorAll('#assetList input[name="assets"]:checked').forEach(function(a) { p.append('services', a.value); });
      window.location.href = '?' + p.toString();
    });
  });
  updateCount();
})();

/* ── Environment picker (single-select, auto-navigates on change; resets asset selection) ── */
(function() {
  var picker   = document.getElementById('envPicker');
  var trigger  = document.getElementById('envTrigger');
  var dropdown = document.getElementById('envDropdown');
  if (!picker) return;

  function updateLabel() {
    var sel = document.querySelector('#envList input[name="environment"]:checked');
    document.getElementById('envCount').textContent = sel ? (sel.dataset.label || sel.value) : '';
  }
  function toggleDropdown(open) {
    trigger.classList.toggle('open', open);
    dropdown.classList.toggle('open', open);
  }

  trigger.addEventListener('click', function(e) { e.stopPropagation(); toggleDropdown(!dropdown.classList.contains('open')); });
  document.addEventListener('click', function(e) { if (!picker.contains(e.target)) toggleDropdown(false); });
  document.addEventListener('keydown', function(e) { if (e.key === 'Escape') toggleDropdown(false); });

  document.querySelectorAll('#envList input[name="environment"]').forEach(function(b) {
    b.addEventListener('change', function() {
      var p = appendDateParams(new URLSearchParams());
      p.set('environment', b.value);
      window.location.href = '?' + p.toString();
    });
  });
  updateLabel();
})();

/* ── Container picker ── */
(function() {
  var picker   = document.getElementById('servicePicker');
  var trigger  = document.getElementById('serviceTrigger');
  var dropdown = document.getElementById('serviceDropdown');
  var search   = document.getElementById('serviceSearch');

  function updateCount() {
    var boxes = document.querySelectorAll('#serviceList input');
    document.getElementById('serviceCount').textContent =
      Array.from(boxes).filter(function(b) { return b.checked; }).length + ' / ' + boxes.length + ' services';
  }
  function toggleDropdown(open) {
    trigger.classList.toggle('open', open);
    dropdown.classList.toggle('open', open);
    if (open) { search.value = ''; search.dispatchEvent(new Event('input')); search.focus(); }
  }

  trigger.addEventListener('click', function(e) { e.stopPropagation(); toggleDropdown(!dropdown.classList.contains('open')); });
  document.addEventListener('click', function(e) { if (!picker.contains(e.target)) toggleDropdown(false); });
  document.addEventListener('keydown', function(e) { if (e.key === 'Escape') toggleDropdown(false); });

  search.addEventListener('input', function() {
    var q = search.value.toLowerCase();
    document.querySelectorAll('#serviceList .container-item').forEach(function(item) {
      item.classList.toggle('hidden', !!q && !item.querySelector('span').textContent.toLowerCase().includes(q));
    });
  });

  document.getElementById('btnSelectAll').addEventListener('click', function() {
    document.querySelectorAll('#serviceList .container-item:not(.hidden) input').forEach(function(b) { b.checked = true; });
    updateCount();
  });
  document.getElementById('btnSelectNone').addEventListener('click', function() {
    document.querySelectorAll('#serviceList .container-item:not(.hidden) input').forEach(function(b) { b.checked = false; });
    updateCount();
  });
  document.querySelectorAll('#serviceList input').forEach(function(b) { b.addEventListener('change', updateCount); });
  updateCount();
})();

/* ── Reset ── */
document.getElementById('btnReset').addEventListener('click', function() {
  window.location.href = window.location.pathname;
});

/* ── Form submit ── */
document.getElementById('filterForm').addEventListener('submit', function(e) {
  e.preventDefault();
  var p = appendDateParams(new URLSearchParams());
  var env = document.querySelector('#envList input[name="environment"]:checked');
  if (env) p.set('environment', env.value);
  // Encode the selection as services=<clusterId>[=svc1,svc2,...] per selected asset. The service
  // picker is global, so the chosen services apply to every selected asset (empty => all services).
  // When every service is checked we omit the list entirely (empty => all): this keeps the URL short
  // and avoids overflowing the request-line limit (414) on wide selections.
  var allServiceBoxes = document.querySelectorAll('#serviceList input');
  var selectedServices = Array.from(allServiceBoxes).filter(function(b) { return b.checked; }).map(function(b) { return b.value; });
  var allSelected = selectedServices.length === allServiceBoxes.length;
  var serviceSuffix = (selectedServices.length && !allSelected) ? '=' + selectedServices.join(',') : '';
  document.querySelectorAll('#assetList input[name="assets"]:checked').forEach(function(b) { p.append('services', b.value + serviceSuffix); });
  window.location.href = '?' + p.toString();
});

/* ── Keep the resources panel the same height as the shares chart; scroll its overflow ── */
(function() {
  var row = document.querySelector('.overview-row');
  if (!row) return;
  var shares = row.querySelector('.chart-card');
  var panel  = row.querySelector('.kpi-card');
  if (!shares || !panel) return;
  function sync() { panel.style.height = shares.offsetHeight + 'px'; }
  sync();
  window.addEventListener('resize', sync);
})();

/* ── Asset detail modal (Services tracked panel) ── */
(function() {
  var overlay = document.getElementById('assetModal');
  if (!overlay) return;
  var body    = document.getElementById('assetModalBody');
  var titleEl = document.getElementById('assetModalTitle');
  var closeBtn = document.getElementById('assetModalClose');

  function open(block) {
    var detail = block.querySelector('.asset-detail');
    var name   = block.querySelector('.asset-name');
    titleEl.textContent = name ? name.textContent : '';
    body.innerHTML = detail ? detail.innerHTML : '';
    overlay.hidden = false;
    document.body.classList.add('modal-open');
    closeBtn.focus();
  }
  function close() {
    overlay.hidden = true;
    body.innerHTML = '';
    document.body.classList.remove('modal-open');
  }

  document.querySelectorAll('.asset-head').forEach(function(head) {
    head.addEventListener('click', function() { open(head.closest('.asset-block')); });
    head.addEventListener('keydown', function(e) {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(head.closest('.asset-block')); }
    });
  });
  closeBtn.addEventListener('click', close);
  overlay.addEventListener('click', function(e) { if (e.target === overlay) close(); });
  document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape' && !overlay.hidden) close();
  });
})();

/* ── Per-service collapsible detail charts ── */
var detailCharts = {};

function makeDetailChart(id, embedded, use, color, unit) {
  return new Chart(document.getElementById(id), {
    type: 'bar',
    data: {
      labels: ['Embedded', 'Use'],
      datasets: [{ data: [embedded, use], backgroundColor: [color + 'cc', color + '55'], borderColor: [color, color], borderWidth: 1 }]
    },
    options: {
      indexAxis: 'y', responsive: true, maintainAspectRatio: false,
      plugins: { legend: { display: false }, tooltip: { mode: 'index' } },
      scales: {
        x: { grid: { color: BORDER }, title: { display: true, text: unit, color: color } },
        y: { grid: { display: false }, ticks: { color: TEXT, font: { size: 12, weight: '600' } } }
      }
    }
  });
}

document.querySelectorAll('.service-summary').forEach(function(row) {
  row.addEventListener('click', function() {
    var idx    = parseInt(row.dataset.idx, 10);
    var detail = document.getElementById('detail-' + idx);
    var open   = !detail.classList.toggle('collapsed');
    row.querySelector('.row-chevron').classList.toggle('open', open);
    if (!open) return;
    if (!detailCharts[idx]) {
      var i = idx - 1;
      detailCharts[idx] = [
        { id: 'detailGwp-', emb: gwpEmbedded[i], use: gwpUse[i],  color: GREEN,  unit: 'kgCO2eq' },
        { id: 'detailPe-',  emb: peEmbedded[i],  use: peUse[i],   color: BLUE,   unit: 'MJ'      },
        { id: 'detailAdp-', emb: adpEmbedded[i], use: adpUse[i],  color: YELLOW, unit: 'kgSbeq'  }
      ].map(function(m) { return makeDetailChart(m.id + idx, m.emb, m.use, m.color, m.unit); });
    } else {
      // Replay the bar animation on every reopen: reset to the initial state, then re-render.
      detailCharts[idx].forEach(function(chart) { chart.reset(); chart.update(); });
    }
  });
});
