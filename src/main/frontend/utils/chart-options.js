import { DATE_LOCALE } from './utc-date.js';

export const METRICS = {
  gwp: { color: '#16a34a', accent: 'green', unit: 'kgCO2eq' },
  pe: { color: '#2563eb', accent: 'blue', unit: 'MJ' },
  adp: { color: '#ca8a04', accent: 'yellow', unit: 'kgSbeq' }
};

export function cssVar(name, fallback) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;
}

export function palette() {
  const dark = document.documentElement.getAttribute('data-theme') === 'dark';
  return {
    metrics: Object.fromEntries(Object.entries(METRICS).map(([key, metric]) => [
      key, { ...metric, color: cssVar(`--accent-${metric.accent}`, metric.color) }
    ])),
    stroke: cssVar('--text-dim', dark ? '#6b8478' : '#9ab4a6'),
    border: cssVar('--border', dark ? '#2a3a34' : '#e3ede8'),
    text: cssVar('--text-muted', dark ? '#a3bbb0' : '#4d7060'),
    useAlpha: dark ? '80' : '33'
  };
}

export function chartColors() {
  const colors = palette().metrics;
  return [colors.gwp.color, colors.pe.color, colors.adp.color, '#0891b2', '#7c3aed',
    cssVar('--accent-orange', '#ea580c'), '#db2777', '#0284c7'];
}

export function defaults() {
  const colors = palette();
  Chart.defaults.color = colors.text;
  Chart.defaults.borderColor = colors.border;
  Chart.defaults.font.family = "'DM Sans', sans-serif";
  Chart.defaults.font.size = 12;
  Chart.defaults.font.weight = '500';
}

export function wrapLabel(label, maximum = 18) {
  const lines = [];
  let current = '';
  String(label).split(' ').forEach(word => {
    if (current && `${current} ${word}`.length > maximum) {
      lines.push(current);
      current = word;
    } else current = current ? `${current} ${word}` : word;
  });
  if (current) lines.push(current);
  return lines;
}

export function metricDatasets(values, color) {
  return [
    { label: 'Embedded', data: values.map(value => value.embedded), backgroundColor: color + 'cc', borderColor: color, borderWidth: 1 },
    { label: 'Use', data: values.map(value => value.use), backgroundColor: color + '33', borderColor: color, borderWidth: 1 }
  ];
}

export function horizontalConfig(services, metric) {
  const colors = palette();
  const { color, unit } = colors.metrics[metric];
  return {
    type: 'bar',
    data: { labels: services.map(service => service.name), datasets: metricDatasets(services.map(service => service[metric]), color) },
    options: {
      indexAxis: 'y', responsive: true, maintainAspectRatio: false,
      datasets: { bar: { categoryPercentage: 0.5, barPercentage: 0.9, maxBarThickness: 12 } },
      plugins: {
        legend: { position: 'top', labels: { boxWidth: 10, padding: 14 } },
        tooltip: { mode: 'index', callbacks: { title: items => `${services[items[0].dataIndex].asset} / ${services[items[0].dataIndex].name}` } }
      },
      scales: {
        x: { stacked: true, grid: { color: colors.border }, title: { display: true, text: unit, color } },
        y: { stacked: true, grid: { color: colors.border }, ticks: {
          font: { size: 11, lineHeight: 1.05 },
          callback(value) { return wrapLabel(this.getLabelForValue(value)); }
        } }
      }
    }
  };
}

export function sharesConfig(services) {
  const colors = chartColors();
  return {
    type: 'doughnut',
    data: {
      labels: services.map(service => service.name),
      datasets: [{ data: services.map(service => service.share), backgroundColor: colors.map(color => color + 'cc'), borderColor: colors, borderWidth: 1, hoverOffset: 8 }]
    },
    options: {
      responsive: true, maintainAspectRatio: false, cutout: '62%',
      plugins: { legend: { display: false }, tooltip: { callbacks: {
        title: items => `${services[items[0].dataIndex].asset} / ${services[items[0].dataIndex].name}`,
        label: context => ` ${(context.parsed * 100).toFixed(1)}%`
      } } }
    }
  };
}

export function detailConfig(service, metric) {
  const colors = palette();
  const { color, unit } = colors.metrics[metric];
  return {
    type: 'bar',
    data: { labels: ['Embedded', 'Use'], datasets: [{
      data: [service[metric].embedded, service[metric].use],
      backgroundColor: [color + 'cc', color + '55'], borderColor: [color, color], borderWidth: 1
    }] },
    options: {
      indexAxis: 'y', responsive: true, maintainAspectRatio: false,
      plugins: { legend: { display: false }, tooltip: { mode: 'index' } },
      scales: {
        x: { grid: { color: colors.border }, title: { display: true, text: unit, color } },
        y: { grid: { display: false }, ticks: { color: colors.text, font: { size: 12, weight: '600' } } }
      }
    }
  };
}

export function timelineConfig(timeline, series, mode) {
  const colors = palette();
  const accent = colors.metrics[series.key];
  const hourly = timeline.stepMs < 86400000;
  const tickFormat = new Intl.DateTimeFormat(DATE_LOCALE, hourly
    ? { timeZone: 'UTC', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }
    : { timeZone: 'UTC', month: 'short', day: 'numeric' });
  const titleFormat = new Intl.DateTimeFormat(DATE_LOCALE, hourly
    ? { timeZone: 'UTC', year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }
    : { timeZone: 'UTC', year: 'numeric', month: 'short', day: 'numeric' });
  const amount = (value, digits = 2) => !value ? '0'
    : Math.abs(value) < 0.001 ? value.toExponential(digits) : String(Number(value.toPrecision(3)));
  const datasets = metricDatasets(series.buckets, accent.color);
  datasets[1].backgroundColor = accent.color + colors.useAlpha;
  datasets.forEach((dataset, index) => {
    if (mode === 'line') Object.assign(dataset, {
      borderColor: colors.stroke, borderWidth: 1.25,
      fill: index === 0 ? 'origin' : '-1', pointRadius: 0, pointHoverRadius: 3,
      cubicInterpolationMode: 'monotone'
    });
    else dataset.borderWidth = timeline.starts.length > 120 ? 0 : 1;
  });
  return {
    type: mode,
    data: { labels: timeline.starts.map(start => tickFormat.format(new Date(start))), datasets },
    options: {
      responsive: true, maintainAspectRatio: false,
      datasets: { bar: { categoryPercentage: 0.9, barPercentage: 0.95 } },
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { position: 'top', labels: { boxWidth: 10, padding: 14 } },
        tooltip: { callbacks: {
          title: items => `${titleFormat.format(new Date(timeline.starts[items[0].dataIndex]))} UTC`,
          label: context => ` ${context.dataset.label}: ${amount(context.parsed.y)} ${series.unit}`,
          footer: items => `Total: ${amount(items.reduce((sum, item) => sum + item.parsed.y, 0))} ${series.unit}`
        } }
      },
      scales: {
        x: { stacked: true, grid: { display: false }, ticks: { autoSkip: true, maxTicksLimit: 12, maxRotation: 0, font: { size: 11 } } },
        y: { stacked: true, beginAtZero: true, grid: { color: colors.border },
          title: { display: true, text: series.unit, color: accent.color },
          ticks: { font: { size: 11 }, callback: value => amount(value, 1) } }
      }
    }
  };
}
