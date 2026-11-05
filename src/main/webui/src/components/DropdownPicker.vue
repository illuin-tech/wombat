<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, ref, watch} from 'vue'

interface PickerOption {
  value: string
  label: string
}

const props = withDefaults(defineProps<{
  label: string
  options: PickerOption[]
  modelValue: string[]
  summary: string
  multiple?: boolean
  searchable?: boolean
  actions?: boolean
  disabled?: boolean
  pickerId?: string
  inputName?: string
}>(), {multiple: true, searchable: false, actions: false, disabled: false, inputName: undefined})
const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>()
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const searchInput = ref<HTMLInputElement | null>(null)
const opened = ref(false)
const search = ref('')
const query = computed(() => search.value.trim().toLowerCase())
const prefix = computed(() => props.pickerId?.replace(/Picker$/, '') ?? '')
const matches = (option: PickerOption) => !query.value || option.label.toLowerCase().includes(query.value)
const visibleOptions = computed(() => props.options.filter(matches))

function close(): void {
  opened.value = false
}

function toggleOpen(): void {
  if (opened.value) {
    close()
    return
  }
  search.value = ''
  opened.value = true
}

function onDocumentClick(event: MouseEvent): void {
  if (root.value && !root.value.contains(event.target as Node)) close()
}

function onDocumentKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && opened.value) {
    close()
    trigger.value?.focus()
  }
}

watch(opened, async (isOpen) => {
  if (isOpen) {
    document.addEventListener('click', onDocumentClick)
    document.addEventListener('keydown', onDocumentKeydown)
    await nextTick()
    searchInput.value?.focus()
  } else {
    document.removeEventListener('click', onDocumentClick)
    document.removeEventListener('keydown', onDocumentKeydown)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onDocumentKeydown)
})

function toggle(value: string): void {
  if (!props.multiple) {
    emit('update:modelValue', [value])
    close()
    return
  }
  const next = new Set(props.modelValue)
  next.has(value) ? next.delete(value) : next.add(value)
  emit('update:modelValue', [...next])
}

function selectVisible(): void {
  emit('update:modelValue', [...new Set([...props.modelValue, ...visibleOptions.value.map(option => option.value)])])
}

function deselectVisible(): void {
  const visible = new Set(visibleOptions.value.map(option => option.value))
  emit('update:modelValue', props.modelValue.filter(value => !visible.has(value)))
}
</script>

<template>
  <div ref="root" class="container-picker" :id="pickerId">
    <button :id="prefix ? `${prefix}Trigger` : undefined" ref="trigger" class="container-trigger" :class="{open: opened}" type="button" :disabled="disabled" :aria-label="label" :aria-expanded="opened" :aria-controls="prefix ? `${prefix}Dropdown` : undefined" @click="toggleOpen">
      <span>{{ summary }}</span>
      <svg width="10" height="10" viewBox="0 0 10 10" fill="none" class="trigger-chevron">
        <path d="M2 3.5L5 6.5L8 3.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
      </svg>
    </button>
    <div :id="prefix ? `${prefix}Dropdown` : undefined" class="container-dropdown" :class="{open: opened}" :aria-hidden="!opened">
      <input v-if="searchable" :id="prefix ? `${prefix}Search` : undefined" ref="searchInput" v-model="search" type="text" class="container-search" placeholder="Search…" autocomplete="off" />
      <div v-if="actions && multiple" class="container-actions">
        <button id="btnSelectAll" type="button" @click="selectVisible">Select all</button>
        <button id="btnSelectNone" type="button" @click="deselectVisible">Select none</button>
      </div>
      <div :id="prefix ? `${prefix}List` : undefined" class="container-list">
        <label v-for="option in options" :key="option.value" class="container-item" :class="{hidden: !matches(option)}">
          <input :type="multiple ? 'checkbox' : 'radio'" :name="inputName ?? label.toLowerCase()" :value="option.value" :data-label="option.label" :checked="modelValue.includes(option.value)" @change="toggle(option.value)" />
          <span>{{ option.label }}</span>
        </label>
      </div>
    </div>
  </div>
</template>
