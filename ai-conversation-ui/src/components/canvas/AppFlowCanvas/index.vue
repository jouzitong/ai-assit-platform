<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useVueFlow, VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import type {
  Connection,
  ConnectionLineOptions,
  ConnectionLineType,
  CoordinateExtent,
  Edge,
  EdgeMouseEvent,
  Node,
} from '@vue-flow/core'

type SnapGrid = [number, number]

const props = withDefaults(defineProps<{
  nodeTypes?: Record<string, unknown>
  canvasExtent?: CoordinateExtent
  nodeExtent?: CoordinateExtent
  snapGrid?: SnapGrid
  snapToGrid?: boolean
  nodesDraggable?: boolean
  nodesConnectable?: boolean
  elementsSelectable?: boolean
  panOnDrag?: boolean
  zoomOnScroll?: boolean
  zoomOnPinch?: boolean
  zoomOnDoubleClick?: boolean
  minZoom?: number
  maxZoom?: number
  connectionLineType?: ConnectionLineType | null
  connectionLineOptions?: ConnectionLineOptions
  fitViewOnInit?: boolean
  fitViewTrigger?: string | number | boolean
  fitViewPadding?: number
  backgroundPatternColor?: string
  backgroundGap?: number
  showControls?: boolean
}>(), {
  nodeTypes: () => ({}),
  canvasExtent: () => [[0, 0], [1600, 900]],
  nodeExtent: undefined,
  snapGrid: () => [24, 24],
  snapToGrid: true,
  nodesDraggable: true,
  nodesConnectable: false,
  elementsSelectable: true,
  panOnDrag: true,
  zoomOnScroll: false,
  zoomOnPinch: false,
  zoomOnDoubleClick: false,
  minZoom: 0.35,
  maxZoom: 1.4,
  connectionLineType: null,
  connectionLineOptions: () => ({}),
  fitViewOnInit: true,
  fitViewTrigger: undefined,
  fitViewPadding: 0.12,
  backgroundPatternColor: 'rgba(148, 163, 184, 0.22)',
  backgroundGap: 24,
  showControls: false,
})

const nodes = defineModel<Node[]>('nodes', { default: () => [] })
const edges = defineModel<Edge[]>('edges', { default: () => [] })
const canvasElement = ref<HTMLElement | null>(null)
const emit = defineEmits<{
  connect: [connection: Connection]
  edgeClick: [event: EdgeMouseEvent]
}>()
const { fitView, getNodes } = useVueFlow()

const resolvedNodeExtent = computed(() => props.nodeExtent || props.canvasExtent)

watch(
  () => props.fitViewTrigger,
  async (trigger) => {
    if (trigger === undefined) {
      return
    }
    await nextTick()
    window.requestAnimationFrame(() => {
      void fitView({ padding: props.fitViewPadding })
    })
  },
)

function getNodeDimensions() {
  return new Map(getNodes.value.map(node => [String(node.id), { ...node.dimensions }]))
}

function getCanvasSize() {
  return {
    width: canvasElement.value?.clientWidth || 0,
    height: canvasElement.value?.clientHeight || 0,
  }
}

defineExpose({ getNodeDimensions, getCanvasSize })
</script>

<template>
  <div ref="canvasElement" class="app-flow-canvas">
    <VueFlow
      v-model:nodes="nodes"
      v-model:edges="edges"
      class="app-flow-canvas__flow"
      :node-types="nodeTypes"
      :snap-to-grid="snapToGrid"
      :snap-grid="snapGrid"
      :nodes-draggable="nodesDraggable"
      :nodes-connectable="nodesConnectable"
      :elements-selectable="elementsSelectable"
      :translate-extent="canvasExtent"
      :node-extent="resolvedNodeExtent"
      :pan-on-drag="panOnDrag"
      :zoom-on-scroll="zoomOnScroll"
      :zoom-on-pinch="zoomOnPinch"
      :zoom-on-double-click="zoomOnDoubleClick"
      :min-zoom="minZoom"
      :max-zoom="maxZoom"
      :connection-line-type="connectionLineType"
      :connection-line-options="connectionLineOptions"
      :fit-view-on-init="fitViewOnInit"
      @connect="emit('connect', $event)"
      @edge-click="emit('edgeClick', $event)"
    >
      <template #node-renderer="slotProps">
        <slot name="node-renderer" v-bind="slotProps" />
      </template>

      <slot />

      <Controls v-if="showControls" />
      <Background :pattern-color="backgroundPatternColor" :gap="backgroundGap" />
    </VueFlow>
  </div>
</template>

<style scoped>
.app-flow-canvas {
  width: 100%;
  max-width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--chat-main-bg);
}

.app-flow-canvas__flow {
  width: 100%;
  height: 100%;
  background: var(--chat-main-bg);
}

.app-flow-canvas :deep(.vue-flow__pane) {
  cursor: grab;
}

.app-flow-canvas :deep(.vue-flow__pane.dragging) {
  cursor: grabbing;
}

.app-flow-canvas :deep(.vue-flow__node) {
  cursor: grab;
}

.app-flow-canvas :deep(.vue-flow__node.dragging) {
  cursor: grabbing;
}
</style>
