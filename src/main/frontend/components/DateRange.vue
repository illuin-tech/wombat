<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import {
  clampStartDay, dateToUtcDay, pickerDateToUtcDay, shiftUtcDay, shiftUtcMonth,
  utcDayDiff, utcDayToPickerDate, utcIsoToEndDay, utcIsoToStartDay
} from '../utils/utc-date.js';

const props = defineProps({
  start: { type: String, required: true },
  end: { type: String, required: true },
  maximum: { type: Number, required: true }
});
const picker = ref(null);
const from = ref(utcIsoToStartDay(props.start));
const to = ref(utcIsoToEndDay(props.end));
const native = ref(false);
let instance;
let timer;
onMounted(() => {
  if (!window.easepick) {
    console.warn('easepick is unavailable; using native date controls');
    native.value = true;
    return;
  }
  const maximum = props.maximum ? Math.max(1, Math.floor(props.maximum / 86400000)) : 0;
  const today = dateToUtcDay(new Date());
  const preset = start => [utcDayToPickerDate(start), utcDayToPickerDate(today)];
  const months = count => preset(shiftUtcMonth(shiftUtcDay(today, 1), -count));
  let clamping = false;
  instance = new window.easepick.create({
    element: picker.value,
    css: ['/public/vendor/easepick.css', '/public/easepick-theme.css'],
    zIndex: 10,
    grid: window.matchMedia('(max-width: 720px)').matches ? 1 : 2,
    calendars: window.matchMedia('(max-width: 720px)').matches ? 1 : 2,
    format: 'YYYY-MM-DD',
    plugins: ['RangePlugin', 'PresetPlugin', 'LockPlugin'],
    RangePlugin: {
      tooltip: true, repick: true, delimiter: ' \u2192 ',
      startDate: from.value ? utcDayToPickerDate(from.value) : undefined,
      endDate: to.value ? utcDayToPickerDate(to.value) : undefined
    },
    PresetPlugin: {
      position: 'bottom',
      customPreset: {
        Today: preset(today), 'Last 7 days': preset(shiftUtcDay(today, -6)),
        'Last 3 months': months(3), 'Last 6 months': months(6), 'Last year': months(12)
      }
    },
    LockPlugin: {
      filter(date, picked) {
        return !!maximum && picked?.length === 1 &&
          Math.abs(utcDayDiff(pickerDateToUtcDay(picked[0].toJSDate()), pickerDateToUtcDay(date.toJSDate()))) > maximum - 1;
      }
    },
    setup(control) {
      control.on('select', event => {
        const { start, end } = event.detail;
        const endDay = end ? pickerDateToUtcDay(end) : '';
        const startDay = clampStartDay(start ? pickerDateToUtcDay(start) : '', endDay, maximum);
        if (startDay) from.value = startDay;
        if (endDay) to.value = endDay;
        if (clamping) { clamping = false; return; }
        if (start && startDay && startDay !== pickerDateToUtcDay(start)) {
          clamping = true;
          timer = setTimeout(() => instance.setDateRange(utcDayToPickerDate(startDay), utcDayToPickerDate(endDay)), 0);
        }
      });
    }
  });
});
onBeforeUnmount(() => { clearTimeout(timer); instance?.destroy(); });
</script>

<template>
  <div class="filter-group date-range-group">
    <span class="filter-label">Date range</span>
    <input id="dateRangePicker" ref="picker" v-show="!native" type="text" class="date-range-picker"
           :data-max-span-ms="maximum" placeholder="Pick range..." readonly/>
    <input id="inputFrom" v-model="from" :type="native ? 'date' : 'hidden'" name="from"
           :class="{ 'date-range-native': native }" aria-label="Start date"/>
    <input id="inputTo" v-model="to" :type="native ? 'date' : 'hidden'" name="to"
           :class="{ 'date-range-native': native }" aria-label="End date"/>
  </div>
</template>
