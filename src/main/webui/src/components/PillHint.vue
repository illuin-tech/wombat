<script lang="ts">
import { ref, type InjectionKey } from 'vue'

/**
 * Tells a pill it sits in a PillHint: it then leaves out its title, whose tooltip would show along the bubble. The pill
 * keeps it anywhere else, where it is its only explanation.
 */
export const IN_PILL_HINT: InjectionKey<boolean> = Symbol('in-pill-hint')

/* The id of the hint shown: one at a time */
const shownHint = ref<string | null>(null)
</script>

<script setup lang="ts">
import { computed, onBeforeUnmount, provide, useId, watch } from 'vue'

/** A pill which explains what it stands for in a bubble, on hover or keyboard focus. */
defineProps<{ hint: string }>()

const BUBBLE_WIDTH = 280
const MARGIN = 8
/* Leaves the pointer time to reach the bubble, which stays while hovered */
const HIDE_DELAY_MS = 150

provide(IN_PILL_HINT, true)

const id = `pill-hint-${useId()}`
const anchor = ref<HTMLElement | null>(null)
const position = ref({ top: 0, left: 0 })
const opened = computed(() => shownHint.value === id)
let hiding: ReturnType<typeof setTimeout> | undefined

function show(): void {
  clearTimeout(hiding)
  if (opened.value) return
  /* Fixed under the pill, and kept within the window: a table's overflow would clip it otherwise */
  const rect = anchor.value!.getBoundingClientRect()
  position.value = {
    top: rect.bottom + 6,
    left: Math.max(MARGIN, Math.min(rect.left, window.innerWidth - BUBBLE_WIDTH - MARGIN)),
  }
  shownHint.value = id
}

function hide(): void {
  clearTimeout(hiding)
  hiding = setTimeout(close, HIDE_DELAY_MS)
}

function close(): void {
  clearTimeout(hiding)
  if (opened.value) shownHint.value = null
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') close()
}

function stopListening(): void {
  document.removeEventListener('keydown', onKeydown)
  window.removeEventListener('scroll', close, true)
  window.removeEventListener('resize', close)
}

watch(opened, isOpen => {
  if (!isOpen) {
    stopListening()
    return
  }
  document.addEventListener('keydown', onKeydown)
  window.addEventListener('scroll', close, true)
  window.addEventListener('resize', close)
})

onBeforeUnmount(() => {
  stopListening()
  close()
})
</script>

<template>
  <span
    ref="anchor"
    class="pill-hint"
    tabindex="0"
    :aria-describedby="opened ? id : undefined"
    @mouseenter="show"
    @mouseleave="hide"
    @focus="show"
    @blur="close"
  >
    <slot />
  </span>
  <Teleport to="body">
    <span
      v-if="opened"
      :id="id"
      class="pill-hint-bubble"
      role="tooltip"
      :style="{ top: `${position.top}px`, left: `${position.left}px`, maxWidth: `${BUBBLE_WIDTH}px` }"
      @mouseenter="show"
      @mouseleave="hide"
    >{{ hint }}</span>
  </Teleport>
</template>
