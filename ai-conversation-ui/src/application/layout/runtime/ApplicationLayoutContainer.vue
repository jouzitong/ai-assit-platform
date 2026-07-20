<script setup lang="ts">
import { computed, type CSSProperties } from 'vue'
import type { ApplicationLayoutKind } from '../registry'

const props = defineProps<{
  kind: ApplicationLayoutKind
  layout?: Record<string, unknown>
}>()

const containerStyle = computed<CSSProperties>(() => {
  const layout = props.layout || {}
  const style: CSSProperties = {}
  const allowed = [
    'gridTemplateColumns',
    'gridTemplateRows',
    'gap',
    'padding',
    'alignItems',
    'justifyItems',
    'justifyContent',
    'width',
    'height',
    'minHeight',
  ] as const

  allowed.forEach((key) => {
    const value = layout[key]
    if (typeof value === 'string' || typeof value === 'number') {
      Object.assign(style, { [key]: value })
    }
  })

  if (props.kind === 'grid' && !style.gridTemplateColumns) {
    style.gridTemplateColumns = 'repeat(auto-fit, minmax(min(240px, 100%), 1fr))'
  }
  if (props.kind === 'split' && !style.gridTemplateColumns) {
    style.gridTemplateColumns = 'minmax(0, 1fr) minmax(0, 2fr)'
  }
  if (props.kind === 'stack') {
    style.flexDirection = layout.direction === 'row' ? 'row' : 'column'
    if (layout.wrap === true) {
      style.flexWrap = 'wrap'
    }
  }

  return style
})
</script>

<template>
  <section
    class="application-layout-container"
    :class="`application-layout-container--${kind}`"
    :style="containerStyle"
  >
    <slot />
  </section>
</template>

<style scoped>
.application-layout-container {
  width: 100%;
  min-width: 0;
  min-height: 0;
}

.application-layout-container--page,
.application-layout-container--container,
.application-layout-container--section,
.application-layout-container--sheet,
.application-layout-container--stack {
  display: flex;
  flex-direction: column;
}

.application-layout-container--grid,
.application-layout-container--split {
  display: grid;
}

.application-layout-container--section {
  padding: var(--app-space-4);
  border: 1px solid var(--app-border-subtle);
  border-radius: var(--app-radius-xl);
  background: var(--app-surface-raised);
}

.application-layout-container--sheet {
  padding: var(--app-space-6);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: var(--app-surface-solid);
  box-shadow: var(--app-shadow-sm);
}

@container render-json-runtime (max-width: 720px) {
  .application-layout-container--split {
    grid-template-columns: minmax(0, 1fr) !important;
  }
}

@media print {
  .application-layout-container--sheet {
    padding: 0;
    border: 0;
    box-shadow: none;
    break-after: page;
  }
}
</style>
