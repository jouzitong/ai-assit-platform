<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useResponsiveInteractionScale } from '../../../composables/useResponsiveViewport'
import type { DashboardCanvasItem } from './types'

type CanvasInteraction = {
  id: string
  type: 'drag' | 'resize'
  startClientX: number
  startClientY: number
  origin: DashboardCanvasItem
  columnWidth: number
  rowStep: number
  viewportScale: number
}

const props = withDefaults(defineProps<{
  columns?: number
  rowHeight?: number
  gap?: number
  minRows?: number
  draggable?: boolean
  resizable?: boolean
  readonly?: boolean
}>(), {
  columns: 12,
  rowHeight: 72,
  gap: 12,
  minRows: 6,
  draggable: true,
  resizable: true,
  readonly: false,
})

const layout = defineModel<DashboardCanvasItem[]>('layout', { default: () => [] })
const canvasRef = ref<HTMLElement | null>(null)
const activeInteraction = ref<CanvasInteraction | null>(null)
const responsiveInteractionScale = useResponsiveInteractionScale()

const canvasStyle = computed(() => {
  const rowCount = Math.max(
    props.minRows,
    ...layout.value.map((item) => item.y + item.h),
  )
  return {
    '--dashboard-canvas-columns': String(props.columns),
    '--dashboard-canvas-row-height': `${props.rowHeight}px`,
    '--dashboard-canvas-gap': `${props.gap}px`,
    gridTemplateRows: `repeat(${rowCount}, minmax(${props.rowHeight}px, ${props.rowHeight}px))`,
  }
})

const resolveItemStyle = (item: DashboardCanvasItem) => ({
  gridColumn: `${item.x + 1} / span ${item.w}`,
  gridRow: `${item.y + 1} / span ${item.h}`,
})

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max)

const patchItem = (id: string, patch: Partial<DashboardCanvasItem>) => {
  layout.value = layout.value.map((item) => item.id === id ? { ...item, ...patch } : item)
}

const resolveColumnWidth = (viewportScale: number) => {
  if (!canvasRef.value) {
    return 1
  }
  const canvasWidth = canvasRef.value.getBoundingClientRect().width / viewportScale
  return (canvasWidth - (props.columns - 1) * props.gap) / props.columns
}

const startInteraction = (item: DashboardCanvasItem, type: CanvasInteraction['type'], event: PointerEvent) => {
  if (props.readonly || (type === 'drag' && !props.draggable) || (type === 'resize' && !props.resizable)) {
    return
  }
  event.preventDefault()
  event.stopPropagation()
  const viewportScale = responsiveInteractionScale.value
  activeInteraction.value = {
    id: item.id,
    type,
    startClientX: event.clientX,
    startClientY: event.clientY,
    origin: { ...item },
    columnWidth: resolveColumnWidth(viewportScale),
    rowStep: props.rowHeight + props.gap,
    viewportScale,
  }
  window.addEventListener('pointermove', handlePointerMove)
  window.addEventListener('pointerup', stopInteraction)
}

const handlePointerMove = (event: PointerEvent) => {
  const interaction = activeInteraction.value
  if (!interaction) {
    return
  }

  const deltaX = (event.clientX - interaction.startClientX) / interaction.viewportScale
  const deltaY = (event.clientY - interaction.startClientY) / interaction.viewportScale
  const deltaColumns = Math.round(deltaX / (interaction.columnWidth + props.gap))
  const deltaRows = Math.round(deltaY / interaction.rowStep)
  const { origin } = interaction

  if (interaction.type === 'drag') {
    patchItem(interaction.id, {
      x: clamp(origin.x + deltaColumns, 0, props.columns - origin.w),
      y: Math.max(0, origin.y + deltaRows),
    })
    return
  }

  patchItem(interaction.id, {
    w: clamp(origin.w + deltaColumns, origin.minW || 1, props.columns - origin.x),
    h: Math.max(origin.minH || 1, origin.h + deltaRows),
  })
}

const stopInteraction = () => {
  activeInteraction.value = null
  window.removeEventListener('pointermove', handlePointerMove)
  window.removeEventListener('pointerup', stopInteraction)
}

onBeforeUnmount(stopInteraction)
</script>

<template>
  <div
    ref="canvasRef"
    class="app-dashboard-canvas"
    :class="{ 'is-readonly': readonly }"
    :style="canvasStyle"
  >
    <article
      v-for="item in layout"
      :key="item.id"
      class="app-dashboard-canvas__item"
      :style="resolveItemStyle(item)"
    >
      <header
        v-if="item.title"
        class="app-dashboard-canvas__item-header"
        @pointerdown="startInteraction(item, 'drag', $event)"
      >
        <span>{{ item.title }}</span>
      </header>

      <div class="app-dashboard-canvas__item-body">
        <slot name="widget" :item="item" />
      </div>

      <button
        v-if="!readonly && resizable"
        class="app-dashboard-canvas__resize"
        type="button"
        aria-label="调整组件尺寸"
        @pointerdown="startInteraction(item, 'resize', $event)"
      />
    </article>
  </div>
</template>

<style scoped>
.app-dashboard-canvas {
  display: grid;
  grid-template-columns: repeat(var(--dashboard-canvas-columns), minmax(0, 1fr));
  grid-auto-rows: var(--dashboard-canvas-row-height);
  gap: var(--dashboard-canvas-gap);
  width: 100%;
  min-width: 0;
  min-height: 100%;
  padding: var(--dashboard-canvas-gap);
  overflow: auto;
  background:
    linear-gradient(var(--chat-followup-border) 1px, transparent 1px),
    linear-gradient(90deg, var(--chat-followup-border) 1px, transparent 1px),
    var(--chat-main-bg);
  background-size:
    calc((100% - (var(--dashboard-canvas-columns) - 1) * var(--dashboard-canvas-gap)) / var(--dashboard-canvas-columns) + var(--dashboard-canvas-gap)) calc(var(--dashboard-canvas-row-height) + var(--dashboard-canvas-gap)),
    calc((100% - (var(--dashboard-canvas-columns) - 1) * var(--dashboard-canvas-gap)) / var(--dashboard-canvas-columns) + var(--dashboard-canvas-gap)) calc(var(--dashboard-canvas-row-height) + var(--dashboard-canvas-gap)),
    auto;
}

.app-dashboard-canvas__item {
  position: relative;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--chat-panel-border);
  border-radius: var(--app-radius-md);
  background: var(--chat-panel-bg);
  box-shadow: var(--chat-composer-shadow);
}

.app-dashboard-canvas__item-header {
  display: flex;
  align-items: center;
  min-width: 0;
  height: 34px;
  padding: 0 var(--app-space-3);
  border-bottom: 1px solid var(--chat-followup-border);
  color: var(--chat-text-title);
  font-size: var(--app-font-size-caption);
  font-weight: 700;
  cursor: grab;
  user-select: none;
}

.app-dashboard-canvas__item-header:active {
  cursor: grabbing;
}

.app-dashboard-canvas__item-body {
  min-width: 0;
  min-height: 0;
  overflow: auto;
  padding: var(--app-space-compact);
}

.app-dashboard-canvas__resize {
  position: absolute;
  right: 4px;
  bottom: 4px;
  width: 16px;
  height: 16px;
  border: 0;
  background:
    linear-gradient(135deg, transparent 0 45%, var(--chat-text-faint) 45% 55%, transparent 55%),
    linear-gradient(135deg, transparent 0 65%, var(--chat-text-faint) 65% 75%, transparent 75%);
  cursor: nwse-resize;
}

.app-dashboard-canvas.is-readonly .app-dashboard-canvas__item-header {
  cursor: default;
}
</style>
