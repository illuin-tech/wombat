<script lang="ts">
type Renderer = (source: string) => string

/* markdown-it is only downloaded once a description is shown, then shared by every view */
let loaded: Renderer | null = null
let loading: Promise<Renderer> | null = null

function loadRenderer(): Promise<Renderer> {
  loading ??= import('markdown-it').then(({ default: MarkdownIt }) => {
    /* Raw HTML is escaped: descriptions are written by users and shown to others */
    const markdown = new MarkdownIt({ html: false, linkify: true, breaks: true })
    const defaultRender = markdown.renderer.rules.link_open ?? ((tokens, index, options, _env, self) => self.renderToken(tokens, index, options))
    markdown.renderer.rules.link_open = (tokens, index, options, env, self) => {
      tokens[index].attrSet('target', '_blank')
      tokens[index].attrSet('rel', 'noopener noreferrer')
      return defaultRender(tokens, index, options, env, self)
    }
    loaded = source => markdown.render(source)
    return loaded
  })
  loading.catch(() => { loading = null })
  return loading
}
</script>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'

const props = defineProps<{ source: string | null | undefined }>()
const render = shallowRef<Renderer | null>(loaded)
const failed = ref(false)
/* Only what was waited for comes into focus: once loaded, descriptions render as they are */
const waited = loaded === null

if (!render.value)
  loadRenderer().then(renderer => { render.value = renderer }, () => { failed.value = true })

const html = computed(() => render.value?.(props.source ?? '') ?? null)
</script>

<template>
  <div class="markdown-view" :aria-busy="html === null && !failed">
    <div v-if="html !== null" class="markdown-view-content" :class="{ 'blur-reveal': waited }" v-html="html" />
    <!-- The source stands in, blurred, until it can be rendered; as is if the renderer cannot be loaded -->
    <p v-else class="markdown-view-source" :class="{ 'blur-pending': !failed }">{{ source }}</p>
  </div>
</template>
