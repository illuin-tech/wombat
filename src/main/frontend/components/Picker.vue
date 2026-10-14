<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';

const props = defineProps({
  kind: { type: String, required: true },
  prefix: { type: String, required: true },
  options: { type: Array, required: true },
  modelValue: { type: Array, required: true },
  radio: Boolean,
  searchable: Boolean
});
const emit = defineEmits(['update:modelValue', 'change']);
const expanded = ref(false);
const query = ref('');
const root = ref(null);
const search = ref(null);
const visible = computed(() => props.options.filter(option => option.label.toLowerCase().includes(query.value.toLowerCase())));
const label = computed(() => props.radio
  ? props.options.find(option => props.modelValue.includes(option.value))?.label || ''
  : `${props.modelValue.length} / ${props.options.length} ${props.kind}`);
function update(values) {
  emit('update:modelValue', values);
  emit('change', values);
}
function changed(option, checked) {
  update(props.radio ? [option.value] : checked
    ? [...props.modelValue, option.value]
    : props.modelValue.filter(value => value !== option.value));
}
function select(checked) {
  const shown = new Set(visible.value.map(option => option.value));
  // Bulk service selection intentionally applies only to the current search results.
  emit('update:modelValue', checked
    ? [...new Set([...props.modelValue, ...shown])]
    : props.modelValue.filter(value => !shown.has(value)));
}
async function toggle() {
  expanded.value = !expanded.value;
  if (expanded.value) {
    query.value = '';
    await nextTick();
    search.value?.focus();
  }
}
function outside(event) { if (!root.value.contains(event.target)) expanded.value = false; }
function escape(event) { if (event.key === 'Escape') expanded.value = false; }
onMounted(() => {
  document.addEventListener('click', outside);
  document.addEventListener('keydown', escape);
});
onBeforeUnmount(() => {
  document.removeEventListener('click', outside);
  document.removeEventListener('keydown', escape);
});
</script>

<template>
  <div :id="`${prefix}Picker`" ref="root" class="container-picker" :data-kind="kind">
    <button :id="`${prefix}Trigger`" type="button" class="container-trigger"
            :class="{ open: expanded }" :aria-expanded="expanded" @click.stop="toggle">
      <span :id="`${prefix}Count`">{{ label }}</span>
      <svg width="10" height="10" viewBox="0 0 10 10" fill="none" class="trigger-chevron">
        <path d="M2 3.5L5 6.5L8 3.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
      </svg>
    </button>
    <div class="container-dropdown" :class="{ open: expanded }">
      <template v-if="searchable">
        <input :id="`${prefix}Search`" ref="search" v-model="query" type="text"
               class="container-search" placeholder="Search..." autocomplete="off"/>
        <div class="container-actions">
          <button id="btnSelectAll" type="button" @click="select(true)">Select all</button>
          <button id="btnSelectNone" type="button" @click="select(false)">Select none</button>
        </div>
      </template>
      <div :id="`${prefix}List`" class="container-list">
        <label v-for="option in options" v-show="visible.includes(option)" :key="option.value" class="container-item">
          <input :type="radio ? 'radio' : 'checkbox'" :name="kind" :value="option.value"
                 :checked="modelValue.includes(option.value)" @change="changed(option, $event.target.checked)"/>
          <span>{{ option.label }}</span>
        </label>
      </div>
    </div>
  </div>
</template>
