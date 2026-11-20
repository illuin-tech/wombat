<script setup lang="ts">
import type { OutlineProperty } from '@/pages/modules/model/schemaOutline'

defineProps<{ properties: OutlineProperty[] }>()

/* Long lists, e.g. the countries, fold past these */
const SHOWN_VALUES = 8
</script>

<template>
  <ul class="schema-outline">
    <li v-for="property in properties" :key="property.name" class="schema-property">
      <div class="schema-property-head">
        <span class="schema-label">{{ property.label }}</span>
        <code class="tech-name">{{ property.name }}</code>
        <span class="schema-type">{{ property.type }}</span>
        <span v-if="property.required" class="schema-required">required</span>
        <span v-for="constraint in property.constraints" :key="constraint" class="schema-constraint">{{ constraint }}</span>
        <span v-if="property.defaultValue !== undefined" class="schema-default">default <code class="tech-name">{{ property.defaultValue }}</code></span>
      </div>
      <p v-if="property.description" class="schema-description">{{ property.description }}</p>

      <div v-if="property.values.length > 0" class="schema-values">
        <code v-for="value in property.values.slice(0, SHOWN_VALUES)" :key="value.value" class="tech-name" :title="value.title ? value.value : undefined">
          {{ value.title ?? value.value }}
        </code>
        <details v-if="property.values.length > SHOWN_VALUES" class="schema-more">
          <summary>and {{ property.values.length - SHOWN_VALUES }} more</summary>
          <code v-for="value in property.values.slice(SHOWN_VALUES)" :key="value.value" class="tech-name" :title="value.title ? value.value : undefined">
            {{ value.title ?? value.value }}
          </code>
        </details>
      </div>

      <details v-if="property.properties.length > 0" class="schema-nested" open>
        <summary>{{ property.properties.length }} propert{{ property.properties.length === 1 ? 'y' : 'ies' }}</summary>
        <SchemaOutline :properties="property.properties" />
      </details>

      <div v-if="property.variants.length > 0" class="schema-variants">
        <span class="schema-variants-label">One of:</span>
        <details v-for="variant in property.variants" :key="variant.title" class="schema-variant">
          <summary>
            <span class="schema-label">{{ variant.title }}</span>
            <code v-if="variant.discriminator" class="tech-name">{{ variant.discriminator.name }}: {{ variant.discriminator.value }}</code>
          </summary>
          <p v-if="variant.description" class="schema-description">{{ variant.description }}</p>
          <SchemaOutline :properties="variant.properties" />
        </details>
      </div>
    </li>
  </ul>
</template>
