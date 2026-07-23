<script setup lang="ts">
import { computed, type CSSProperties } from 'vue'
import type { ApplicationLayoutKind } from '../registry'

const props = defineProps<{
  kind: ApplicationLayoutKind
  layout?: Record<string, unknown>
  developerMode?: boolean
}>()

// 网格只属于开发者调试辅助层；layout.showGrid === false 允许在开发模式下关闭。
const showGrid = computed(() => (
  props.kind === 'grid'
  && props.developerMode === true
  && props.layout?.showGrid !== false
))
const gridColumns = computed(() => {
  const value = Number(props.layout?.columns)
  return Number.isInteger(value) && value > 0 ? value : 16
})
const gridRows = computed(() => {
  const value = Number(props.layout?.rows)
  return Number.isInteger(value) && value > 0 ? value : 12
})
const gridCells = computed(() => Array.from(
  { length: gridColumns.value * gridRows.value },
  (_, index) => ({
    row: Math.floor(index / gridColumns.value) + 1,
    column: (index % gridColumns.value) + 1,
  }),
))

const containerStyle = computed<CSSProperties>(() => {
  const layout = props.layout || {}
  const style: CSSProperties = {}
  const allowed = [
    'gridTemplateColumns',
    'gridTemplateRows',
    'gridAutoRows',
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

const gridOverlayStyle = computed<CSSProperties>(() => {
  const layout = props.layout || {}
  const style: CSSProperties = {
    gridTemplateColumns: typeof layout.gridTemplateColumns === 'string'
      ? layout.gridTemplateColumns
      : `repeat(${gridColumns.value}, minmax(0, 1fr))`,
    gridTemplateRows: typeof layout.gridTemplateRows === 'string'
      ? layout.gridTemplateRows
      : `repeat(${gridRows.value}, minmax(0, 1fr))`,
  }
  ;(['gap', 'padding'] as const).forEach((key) => {
    const value = layout[key]
    if (typeof value === 'string' || typeof value === 'number') {
      Object.assign(style, { [key]: value })
    }
  })
  return style
})
</script>

<template>
  <section
    class="application-layout-container"
    :class="[
      `application-layout-container--${kind}`,
      { 'application-layout-container--grid-debug': showGrid },
    ]"
    :style="containerStyle"
  >
    <div
      v-if="showGrid"
      class="application-layout-grid-debug"
      :style="gridOverlayStyle"
      aria-hidden="true"
    >
      <span
        v-for="cell in gridCells"
        :key="`${cell.row}-${cell.column}`"
        class="application-layout-grid-debug__cell"
      >
        <small v-if="cell.row === 1 || cell.column === 1">
          {{ cell.row }}/{{ cell.column }}
        </small>
      </span>
    </div>
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

.application-layout-container--grid-debug {
  position: relative;
}

.application-layout-grid-debug {
  position: absolute;
  inset: 0;
  z-index: 20;
  display: grid;
  pointer-events: none;
}

.application-layout-grid-debug__cell {
  position: relative;
  min-width: 0;
  min-height: 0;
  border: 1px dashed color-mix(in srgb, var(--el-color-primary) 22%, transparent);
  background: color-mix(in srgb, var(--el-color-primary) 1.5%, transparent);
}

.application-layout-grid-debug__cell small {
  position: absolute;
  top: 0.125rem;
  left: 0.125rem;
  padding: 0.0625rem 0.1875rem;
  border-radius: 0.1875rem;
  color: color-mix(in srgb, var(--el-color-primary) 52%, transparent);
  background: color-mix(in srgb, var(--el-bg-color) 68%, transparent);
  font-size: 0.625rem;
  line-height: 1.2;
  opacity: 0.72;
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
