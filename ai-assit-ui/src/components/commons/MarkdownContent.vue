<script setup>
import { computed } from 'vue'
import { renderMarkdown } from '../../utils/markdown'

const props = defineProps({
  content: {
    type: String,
    default: ''
  }
})

const renderedHtml = computed(() => renderMarkdown(props.content))
</script>

<template>
  <div class="markdown-content" v-html="renderedHtml" />
</template>

<style scoped>
.markdown-content {
  color: var(--text);
  line-height: 1.7;
  word-break: break-word;
}

.markdown-content :deep(*) {
  box-sizing: border-box;
}

.markdown-content :deep(:first-child) {
  margin-top: 0;
}

.markdown-content :deep(:last-child) {
  margin-bottom: 0;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin: 1em 0 0.45em;
  color: var(--text);
  font-weight: 700;
  line-height: 1.35;
}

.markdown-content :deep(p),
.markdown-content :deep(ul),
.markdown-content :deep(ol),
.markdown-content :deep(blockquote),
.markdown-content :deep(pre),
.markdown-content :deep(table) {
  margin: 0.75em 0;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  padding-left: 1.4em;
}

.markdown-content :deep(li + li) {
  margin-top: 0.25em;
}

.markdown-content :deep(a) {
  color: var(--brand, #2563eb);
  text-decoration: none;
}

.markdown-content :deep(a:hover) {
  text-decoration: underline;
}

.markdown-content :deep(code) {
  padding: 0.12em 0.38em;
  border-radius: 6px;
  background: color-mix(in srgb, var(--control-bg, #f3f4f6) 78%, #000 2%);
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 0.92em;
}

.markdown-content :deep(pre) {
  overflow-x: auto;
  padding: 14px 16px;
  border: 1px solid var(--stroke);
  border-radius: 12px;
  background: color-mix(in srgb, var(--control-bg, #f3f4f6) 85%, #000 3%);
}

.markdown-content :deep(pre code) {
  padding: 0;
  border-radius: 0;
  background: transparent;
}

.markdown-content :deep(blockquote) {
  margin-left: 0;
  padding: 0.2em 0 0.2em 1em;
  border-left: 3px solid var(--stroke);
  color: var(--text-secondary, #6b7280);
}

.markdown-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  padding: 10px 12px;
  border: 1px solid var(--stroke);
  text-align: left;
  vertical-align: top;
}

.markdown-content :deep(th) {
  background: color-mix(in srgb, var(--surface, #fff) 90%, #000 3%);
  font-weight: 600;
}
</style>
