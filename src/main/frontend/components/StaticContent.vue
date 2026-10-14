<script setup>
import { onMounted, ref } from 'vue';

const props = defineProps({ nodes: { type: Array, required: true } });
const container = ref(null);
// Move already-escaped server DOM without compiling it as a Vue template or reparsing HTML.
onMounted(() => container.value.append(...props.nodes));
</script>

<template>
  <div ref="container"></div>
</template>
