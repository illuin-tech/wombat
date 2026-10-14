<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import StaticContent from './StaticContent.vue';

const props = defineProps({
  id: { type: String, required: true },
  title: { type: String, required: true },
  regime: { type: String, default: '' },
  nodes: { type: Array, required: true },
  trigger: { type: Object, required: true }
});
const opened = ref(false);
const overlay = ref(null);
const closeButton = ref(null);
async function show() {
  opened.value = true;
  document.body.classList.add('modal-open');
  await nextTick();
  closeButton.value?.focus();
}
async function close() {
  opened.value = false;
  await nextTick();
  if (!document.querySelector('.asset-modal-overlay[data-open="true"]')) document.body.classList.remove('modal-open');
  props.trigger.focus();
}
function escape(event) { if (event.key === 'Escape' && opened.value) close(); }
function triggerKey(event) {
  if (event.target === props.trigger && ['Enter', ' '].includes(event.key)) { event.preventDefault(); show(); }
}
function trapFocus(event) {
  if (event.key !== 'Tab') return;
  const controls = [...overlay.value.querySelectorAll('button, a[href], input, [tabindex="0"]')];
  const first = controls[0];
  const last = controls.at(-1);
  if (event.shiftKey && event.target === first) { event.preventDefault(); last.focus(); }
  else if (!event.shiftKey && event.target === last) { event.preventDefault(); first.focus(); }
}
onMounted(() => {
  props.trigger.addEventListener('click', show);
  props.trigger.addEventListener('keydown', triggerKey);
  document.addEventListener('keydown', escape);
});
onBeforeUnmount(() => {
  props.trigger.removeEventListener('click', show);
  props.trigger.removeEventListener('keydown', triggerKey);
  document.removeEventListener('keydown', escape);
  if (opened.value) {
    overlay.value.dataset.open = 'false';
    if (!document.querySelector('.asset-modal-overlay[data-open="true"]')) document.body.classList.remove('modal-open');
  }
});
</script>

<template>
  <div :id="id" ref="overlay" v-show="opened" class="asset-modal-overlay" :data-open="opened"
       @click.self="close" @keydown="trapFocus">
    <div class="asset-modal" role="dialog" aria-modal="true" :aria-labelledby="`${id}-title`">
      <div class="asset-modal-head">
        <div class="asset-modal-heading">
          <span :id="`${id}-title`" class="asset-modal-title">{{ title }}</span>
          <span v-if="regime" class="asset-type-tag measure">{{ regime }}</span>
        </div>
        <button ref="closeButton" type="button" class="asset-modal-close" title="Close" aria-label="Close" @click="close">✕</button>
      </div>
      <StaticContent class="asset-modal-body" :nodes="nodes"/>
    </div>
  </div>
</template>
