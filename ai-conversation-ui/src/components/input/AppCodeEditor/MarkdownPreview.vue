<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '../../../utils/markdown'

const props = withDefaults(defineProps<{
  content?: string
}>(), {
  content: '',
})

const renderedMarkdown = computed(() => renderMarkdown(props.content))
const hasContent = computed(() => Boolean(props.content.trim()))
</script>

<template>
  <section class="app-code-editor-preview" role="region" aria-label="Markdown 预览">
    <div
      v-if="hasContent"
      class="app-code-editor-preview__content"
      v-html="renderedMarkdown"
    />
    <div v-else class="app-code-editor-preview__empty">暂无可预览内容</div>
  </section>
</template>

<style scoped>
.app-code-editor-preview {
  min-width: 0;
  min-height: var(--app-code-editor-min-height);
  max-height: var(--app-code-editor-max-height);
  padding: var(--app-space-5);
  overflow: auto;
  background: var(--app-code-editor-surface-strong);
  color: var(--app-code-editor-text);
}

.app-code-editor-preview__empty {
  display: grid;
  min-height: calc(var(--app-control-height-lg) * 4);
  place-items: center;
  color: var(--app-code-editor-placeholder);
  font-size: var(--app-font-size-body);
}

.app-code-editor-preview__content {
  min-width: 0;
  font-size: var(--app-font-size-body-lg);
  line-height: var(--app-line-height-loose);
  overflow-wrap: anywhere;
}

.app-code-editor-preview__content :deep(h1),
.app-code-editor-preview__content :deep(h2),
.app-code-editor-preview__content :deep(h3),
.app-code-editor-preview__content :deep(h4),
.app-code-editor-preview__content :deep(h5),
.app-code-editor-preview__content :deep(h6) {
  margin: var(--app-space-5) 0 var(--app-space-2);
  color: var(--app-title);
  font-weight: 700;
  line-height: var(--app-line-height-body);
}

.app-code-editor-preview__content :deep(h1) {
  margin-top: 0;
  font-size: var(--app-font-size-title-lg);
}

.app-code-editor-preview__content :deep(h2) {
  font-size: var(--app-font-size-title-md);
}

.app-code-editor-preview__content :deep(h3) {
  font-size: var(--app-font-size-title-sm);
}

.app-code-editor-preview__content :deep(h4),
.app-code-editor-preview__content :deep(h5),
.app-code-editor-preview__content :deep(h6) {
  font-size: var(--app-font-size-body-lg);
}

.app-code-editor-preview__content :deep(p) {
  margin: 0 0 var(--app-space-3);
}

.app-code-editor-preview__content :deep(ul),
.app-code-editor-preview__content :deep(ol) {
  display: grid;
  gap: var(--app-space-tight);
  margin: 0 0 var(--app-space-3);
  padding-inline-start: var(--app-space-6);
}

.app-code-editor-preview__content :deep(li.task-list-item) {
  list-style: none;
}

.app-code-editor-preview__content :deep(li > input[type='checkbox']) {
  margin: 0 var(--app-space-tight) 0 0;
  accent-color: var(--app-accent);
  pointer-events: none;
}

.app-code-editor-preview__content :deep(code) {
  padding: var(--app-space-hairline) var(--app-space-tight);
  border: 1px solid var(--app-code-editor-border-soft);
  border-radius: var(--app-radius-sm);
  background: var(--app-surface-muted);
  color: var(--app-accent);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: var(--app-font-size-caption);
  overflow-wrap: anywhere;
}

.app-code-editor-preview__content :deep(pre) {
  max-width: 100%;
  margin: 0 0 var(--app-space-3);
  padding: var(--app-space-3);
  overflow-x: auto;
  border: 1px solid var(--app-code-editor-border-soft);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-muted);
}

.app-code-editor-preview__content :deep(pre code) {
  display: block;
  min-width: max-content;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--app-code-editor-text);
  line-height: var(--app-line-height-body);
  overflow-wrap: normal;
  tab-size: 2;
  white-space: pre;
  word-break: normal;
}

.app-code-editor-preview__content :deep(a) {
  color: var(--app-accent);
  text-decoration: underline;
  text-underline-offset: var(--app-space-hairline);
}

.app-code-editor-preview__content :deep(hr) {
  margin: var(--app-space-4) 0;
  border: 0;
  border-top: 1px solid var(--app-code-editor-border-soft);
}

.app-code-editor-preview__content :deep(table) {
  display: block;
  width: 100%;
  max-width: 100%;
  margin: 0 0 var(--app-space-3);
  overflow-x: auto;
  border-collapse: collapse;
}

.app-code-editor-preview__content :deep(th),
.app-code-editor-preview__content :deep(td) {
  min-width: calc(var(--app-space-8) * 3);
  padding: var(--app-space-2) var(--app-space-3);
  border: 1px solid var(--app-code-editor-border-soft);
  text-align: start;
  vertical-align: top;
}

.app-code-editor-preview__content :deep(th) {
  background: var(--app-surface-muted);
  color: var(--app-title);
  font-weight: 700;
}

.app-code-editor-preview__content :deep(th[align='center']),
.app-code-editor-preview__content :deep(td[align='center']) {
  text-align: center;
}

.app-code-editor-preview__content :deep(th[align='right']),
.app-code-editor-preview__content :deep(td[align='right']) {
  text-align: end;
}

.app-code-editor-preview__content :deep(blockquote) {
  margin: var(--app-space-3) 0;
  padding: var(--app-space-2) var(--app-space-3);
  border-inline-start: var(--app-space-micro) solid var(--app-accent-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-muted);
  color: var(--app-text-muted);
}

.app-code-editor-preview__content :deep(> :last-child) {
  margin-bottom: 0;
}

.app-code-editor-preview__content :deep(> :first-child) {
  margin-top: 0;
}
</style>
