<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { renderMarkdown } from '../../../utils/markdown'

const props = withDefaults(defineProps<{
  content?: string
  maxLines?: number
}>(), {
  content: '',
  maxLines: 4,
})

const contentId = `collapsible-markdown-${useId()}`
const rootRef = ref<HTMLElement | null>(null)
const contentRef = ref<HTMLElement | null>(null)
const expanded = ref(false)
const overflowing = ref(false)
const measuredLineHeight = ref(0)
let resizeObserver: ResizeObserver | undefined
let animationFrame: number | undefined

const renderedMarkdown = computed(() => renderMarkdown(props.content))
const hasContent = computed(() => Boolean(props.content.trim() && renderedMarkdown.value.trim()))
const normalizedMaxLines = computed(() => {
  const value = Number(props.maxLines)
  return Number.isFinite(value) ? Math.max(1, Math.floor(value)) : 4
})
const collapsedHeight = computed(() => {
  // Keep a useful clamp before the first layout pass; the measured value takes over
  // as soon as the rendered Markdown is attached to the document.
  const lineHeight = measuredLineHeight.value > 0 ? measuredLineHeight.value : 20.8
  return lineHeight * normalizedMaxLines.value
})
const contentStyle = computed(() => ({
  maxHeight: expanded.value ? undefined : `${collapsedHeight.value}px`,
}))

function scheduleMeasure() {
  if (typeof window === 'undefined') return
  if (animationFrame !== undefined) {
    if (typeof window.cancelAnimationFrame === 'function') {
      window.cancelAnimationFrame(animationFrame)
    } else {
      window.clearTimeout(animationFrame)
    }
  }
  const callback = () => {
    animationFrame = undefined
    measureOverflow()
  }
  animationFrame = typeof window.requestAnimationFrame === 'function'
    ? window.requestAnimationFrame(callback)
    : window.setTimeout(callback, 0)
}

function measureOverflow() {
  const content = contentRef.value
  if (!content || !hasContent.value) {
    overflowing.value = false
    expanded.value = false
    return
  }

  const computedStyle = window.getComputedStyle(content)
  const lineHeight = Number.parseFloat(computedStyle.lineHeight)
  const fontSize = Number.parseFloat(computedStyle.fontSize)
  measuredLineHeight.value = Number.isFinite(lineHeight) && lineHeight > 0
    ? lineHeight
    : Number.isFinite(fontSize) && fontSize > 0
      ? fontSize * 1.6
      : 20.8

  // The viewport, rather than the rendered content, owns the max-height. This
  // lets scrollHeight represent the complete Markdown document while collapsed.
  const fullHeight = content.scrollHeight
  const nextOverflowing = fullHeight > collapsedHeight.value + 1
  overflowing.value = nextOverflowing
  if (!nextOverflowing) expanded.value = false
}

function toggleExpanded() {
  if (!overflowing.value) return
  expanded.value = !expanded.value
  void nextTick(scheduleMeasure)
}

watch([renderedMarkdown, normalizedMaxLines], () => {
  expanded.value = false
  overflowing.value = false
  void nextTick(scheduleMeasure)
})

onMounted(() => {
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => scheduleMeasure())
    if (rootRef.value) resizeObserver.observe(rootRef.value)
    if (contentRef.value) resizeObserver.observe(contentRef.value)
  }
  scheduleMeasure()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  if (animationFrame !== undefined && typeof window !== 'undefined') {
    if (typeof window.cancelAnimationFrame === 'function') {
      window.cancelAnimationFrame(animationFrame)
    } else {
      window.clearTimeout(animationFrame)
    }
  }
})
</script>

<template>
  <div
    ref="rootRef"
    class="collapsible-markdown"
    :class="{
      'is-expanded': expanded,
      'is-overflowing': overflowing,
      'is-collapsed': overflowing && !expanded,
    }"
  >
    <div
      :id="contentId"
      class="collapsible-markdown__viewport"
      :style="contentStyle"
    >
      <div
        ref="contentRef"
        class="collapsible-markdown__content"
        v-html="renderedMarkdown"
      />
    </div>

    <div
      v-if="overflowing"
      class="collapsible-markdown__actions"
    >
      <button
        class="collapsible-markdown__toggle"
        type="button"
        :aria-expanded="expanded"
        :aria-controls="contentId"
        :aria-label="expanded ? '收起详细内容' : '查看更多详细内容'"
        :title="expanded ? '点击收起详细内容' : '点击查看更多详细内容'"
        @click="toggleExpanded"
      >
        <span>{{ expanded ? '收起' : '更多' }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.collapsible-markdown {
  --collapsible-markdown-action-surface: var(
    --collapsible-markdown-surface,
    var(--chat-main-bg, var(--app-surface-solid))
  );
  position: relative;
  min-width: 0;
  color: var(--chat-text-muted, var(--app-text-muted));
  font-size: var(--app-font-size-body);
  line-height: var(--app-line-height-loose);
  overflow-wrap: anywhere;
}

.collapsible-markdown__viewport {
  min-width: 0;
  overflow: hidden;
}

.collapsible-markdown__content {
  min-width: 0;
  display: flow-root;
}

.collapsible-markdown__content :deep(h1),
.collapsible-markdown__content :deep(h2),
.collapsible-markdown__content :deep(h3),
.collapsible-markdown__content :deep(h4),
.collapsible-markdown__content :deep(h5),
.collapsible-markdown__content :deep(h6) {
  margin: var(--app-space-3) 0 var(--app-space-1);
  color: var(--chat-text-primary, var(--app-title));
  font-weight: 700;
  line-height: var(--app-line-height-body);
}

.collapsible-markdown__content :deep(h1),
.collapsible-markdown__content :deep(h2) {
  font-size: var(--app-font-size-body-lg);
}

.collapsible-markdown__content :deep(h3),
.collapsible-markdown__content :deep(h4),
.collapsible-markdown__content :deep(h5),
.collapsible-markdown__content :deep(h6) {
  font-size: var(--app-font-size-body);
}

.collapsible-markdown__content :deep(p) {
  margin: 0 0 var(--app-space-2);
}

.collapsible-markdown__content :deep(ul),
.collapsible-markdown__content :deep(ol) {
  display: grid;
  gap: var(--app-space-tight);
  margin: 0 0 var(--app-space-2);
  padding-inline-start: var(--app-space-5);
}

.collapsible-markdown__content :deep(li) {
  padding-inline-start: var(--app-space-hairline);
}

.collapsible-markdown__content :deep(li.task-list-item) {
  list-style: none;
}

.collapsible-markdown__content :deep(li > input[type='checkbox']) {
  margin: 0 var(--app-space-tight) 0 0;
  accent-color: var(--app-accent);
  pointer-events: none;
}

.collapsible-markdown__content :deep(strong) {
  color: var(--chat-text-primary, var(--app-title));
  font-weight: 700;
}

.collapsible-markdown__content :deep(code) {
  padding: var(--app-space-hairline) var(--app-space-tight);
  border: 1px solid var(--chat-panel-border, var(--app-border-subtle));
  border-radius: var(--app-radius-sm);
  background: var(--chat-soft-bg-alt, var(--app-surface-muted));
  color: var(--app-accent);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: var(--app-font-size-caption);
  overflow-wrap: anywhere;
}

.collapsible-markdown__content :deep(pre) {
  max-width: 100%;
  margin: 0 0 var(--app-space-2);
  padding: var(--app-space-2);
  overflow-x: auto;
  border: 1px solid var(--chat-panel-border, var(--app-border-subtle));
  border-radius: var(--app-radius-md);
  background: var(--chat-soft-bg-alt, var(--app-surface-muted));
}

.collapsible-markdown__content :deep(pre code) {
  display: block;
  min-width: max-content;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--chat-text-primary, var(--app-text));
  line-height: var(--app-line-height-body);
  overflow-wrap: normal;
  tab-size: 2;
  white-space: pre;
  word-break: normal;
}

.collapsible-markdown__content :deep(a) {
  color: var(--app-accent);
  text-decoration: underline;
  text-underline-offset: var(--app-space-hairline);
}

.collapsible-markdown__content :deep(hr) {
  margin: var(--app-space-3) 0;
  border: 0;
  border-top: 1px solid var(--chat-panel-border, var(--app-border-subtle));
}

.collapsible-markdown__content :deep(table) {
  display: block;
  width: 100%;
  max-width: 100%;
  margin: 0 0 var(--app-space-2);
  overflow-x: auto;
  border-collapse: collapse;
}

.collapsible-markdown__content :deep(th),
.collapsible-markdown__content :deep(td) {
  min-width: calc(var(--app-space-8) * 3);
  padding: var(--app-space-1) var(--app-space-2);
  border: 1px solid var(--chat-panel-border, var(--app-border-subtle));
  text-align: start;
  vertical-align: top;
}

.collapsible-markdown__content :deep(th) {
  background: var(--chat-soft-bg-alt, var(--app-surface-muted));
  color: var(--chat-text-primary, var(--app-title));
  font-weight: 700;
}

.collapsible-markdown__content :deep(th[align='center']),
.collapsible-markdown__content :deep(td[align='center']) {
  text-align: center;
}

.collapsible-markdown__content :deep(th[align='right']),
.collapsible-markdown__content :deep(td[align='right']) {
  text-align: end;
}

.collapsible-markdown__content :deep(blockquote) {
  margin: var(--app-space-2) 0;
  padding: var(--app-space-2) var(--app-space-3);
  border-inline-start: var(--app-space-micro) solid var(--app-accent-border);
  border-radius: var(--app-radius-md);
  background: var(--chat-soft-bg-alt, var(--app-surface-muted));
  color: var(--chat-text-muted, var(--app-text-muted));
}

.collapsible-markdown__content :deep(> :first-child) {
  margin-top: 0;
}

.collapsible-markdown__content :deep(> :last-child) {
  margin-bottom: 0;
}

.collapsible-markdown__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--app-space-1);
}

.collapsible-markdown.is-collapsed .collapsible-markdown__actions {
  position: absolute;
  right: 0;
  bottom: 0;
  margin-top: 0;
  z-index: 1;
  padding-inline-start: var(--app-space-6);
  background: linear-gradient(
    90deg,
    transparent 0,
    var(--collapsible-markdown-action-surface) 24%,
    var(--collapsible-markdown-action-surface) 100%
  );
  box-shadow: -8px 0 12px -8px var(--chat-panel-border, var(--app-border-subtle));
}

.collapsible-markdown__toggle {
  appearance: none;
  min-height: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  font-size: inherit;
  font-weight: inherit;
  line-height: inherit;
  text-decoration: none;
  text-underline-offset: var(--app-space-hairline);
  vertical-align: baseline;
  white-space: nowrap;
  transition: color 120ms ease;
}

.collapsible-markdown__toggle:hover {
  color: var(--chat-text-primary, var(--app-title));
  text-decoration: underline;
}

.collapsible-markdown__toggle:focus-visible {
  outline: 2px solid var(--app-accent);
  outline-offset: 2px;
  color: var(--chat-text-primary, var(--app-title));
  text-decoration: underline;
}

@media (prefers-reduced-motion: reduce) {
  .collapsible-markdown__toggle {
    transition: none;
  }
}
</style>
