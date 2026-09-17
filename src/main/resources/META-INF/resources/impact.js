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
