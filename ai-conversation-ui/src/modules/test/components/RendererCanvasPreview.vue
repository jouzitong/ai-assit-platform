<script setup lang="ts">
import { computed, markRaw, onBeforeUnmount, onMounted, ref } from 'vue'
import { FullScreen, Share, Star } from '@element-plus/icons-vue'
import AppFlowCanvas from '../../../components/canvas/AppFlowCanvas/index.vue'
import ListMainLayout from '../../../application/renderers/list/ListMainLayout.vue'
import FormMainLayout from '../../../application/renderers/form/FormMainLayout.vue'
import LineChartRenderer from '../../../application/renderers/echarts/LineChartRenderer.vue'
import type { CoordinateExtent, Node } from '@vue-flow/core'
import type { FormRendererSchema } from '../../../application/renderers/form/types'
import type { ListRendererSchema } from '../../../application/renderers/list/types'

type RendererNodeData = {
  title: string
  renderer: 'list' | 'form' | 'line-chart'
}

const nodeTypes = {
  renderer: markRaw({}),
}

const canvasExtent: CoordinateExtent = [[0, 0], [1600, 900]]
const canvasCoreOptions = {
  canvasExtent,
  nodeExtent: canvasExtent,
  snapGrid: [24, 24] as [number, number],
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
  fitViewOnInit: false,
  fitViewPadding: 0.12,
  backgroundGap: 24,
  backgroundPatternColor: 'rgba(148, 163, 184, 0.22)',
  showControls: false,
}
const previewRef = ref<HTMLElement | null>(null)
const isFullscreen = ref(false)
const isFallbackFullscreen = ref(false)

const normalNodePositions = {
  list: { x: 24, y: 36 },
  form: { x: 248, y: 36 },
  chart: { x: 136, y: 228 },
}

const fullscreenNodePositions = {
  list: { x: 72, y: 56 },
  form: { x: 624, y: 56 },
  chart: { x: 348, y: 456 },
}

const buildNodes = (fullscreen: boolean): Node<RendererNodeData>[] => {
  const positions = fullscreen ? fullscreenNodePositions : normalNodePositions
  return [
  {
    id: 'list-renderer',
    type: 'renderer',
      position: positions.list,
    data: { title: 'List Renderer', renderer: 'list' },
  },
  {
    id: 'form-renderer',
    type: 'renderer',
      position: positions.form,
    data: { title: 'Form Renderer', renderer: 'form' },
  },
  {
    id: 'chart-renderer',
    type: 'renderer',
      position: positions.chart,
    data: { title: 'ECharts Renderer', renderer: 'line-chart' },
  },
  ]
}

const nodes = ref<Node<RendererNodeData>[]>(buildNodes(false))

const listSchema: ListRendererSchema = {
  id: 'canvas-list-demo',
  title: '异常任务列表',
  component: 'zg-common-list',
  tab: {
    activeTab: 'all',
    tabs: [
      { key: 'all', label: '全部' },
      { key: 'risk', label: '风险' },
    ],
  },
  fields: [
    { key: 'name', name: 'name', label: '任务', field: ['name'] },
    { key: 'owner', name: 'owner', label: '负责人', field: ['owner'] },
    { key: 'status', name: 'status', label: '状态', field: ['status'] },
  ],
  summary: {
    cards: [
      { key: 'pending', label: '待处理', value: 6, accent: '#f59e0b' },
      { key: 'running', label: '进行中', value: 3, accent: '#60a5fa' },
    ],
  },
  list_config: {
    variant: 'workbench',
    itemType: 'table',
    pagination: { enabled: false },
  },
}

const listRecords = [
  { name: '提现拦截来源核对', owner: 'Risk Ops', status: '进行中' },
  { name: '登录失败地区拆分', owner: 'Data', status: '待处理' },
]

const formSchema: FormRendererSchema = {
  id: 'canvas-form-demo',
  title: '处置计划',
  component: 'zg-common-info',
  form_config: {
    variant: 'workbench',
    columns: 2,
    description: '用于补充执行完成后的负责人和截止时间。',
  },
  fields: [
    { key: 'owner', label: '负责人', field: ['owner'], component: 'zg-input' },
    { key: 'deadline', label: '截止时间', field: ['deadline'], component: 'zg-date' },
    { key: 'summary', label: '复盘口径', field: ['summary'], component: 'zg-textarea', type: 'textarea' },
  ],
  groups: [
    { key: 'base', title: '基础信息', fields: ['owner', 'deadline', 'summary'], columns: 2 },
  ],
  data: {
    owner: 'Risk Ops',
    deadline: '今日 18:00',
    summary: '资金链路优先归因，登录异常同步复核。',
  },
}

const chartProps = computed(() => ({
  categories: ['10:00', '12:00', '14:00', '16:00', '18:00'],
  unit: '%',
  height: isFullscreen.value ? 320 : 150,
  series: [
    { name: '登录失败率', data: [2.1, 2.8, 3.7, 3.1, 2.6], area: true },
    { name: '提现拦截率', data: [1.2, 1.8, 2.4, 2.9, 2.2] },
  ],
}))

const syncFullscreenState = () => {
  if (isFallbackFullscreen.value) {
    return
  }
  const fullscreen = document.fullscreenElement === previewRef.value
  isFullscreen.value = fullscreen
  nodes.value = buildNodes(fullscreen)
}

const toggleFullscreen = async () => {
  if (!previewRef.value) {
    return
  }

  if (isFallbackFullscreen.value) {
    isFallbackFullscreen.value = false
    isFullscreen.value = false
    nodes.value = buildNodes(false)
    return
  }

  if (document.fullscreenElement === previewRef.value) {
    await document.exitFullscreen()
    return
  }

  if (typeof previewRef.value.requestFullscreen === 'function') {
    await previewRef.value.requestFullscreen()
    return
  }

  isFallbackFullscreen.value = true
  isFullscreen.value = true
  nodes.value = buildNodes(true)
}

onMounted(() => {
  document.addEventListener('fullscreenchange', syncFullscreenState)
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', syncFullscreenState)
})
</script>

<template>
  <section
    ref="previewRef"
    :class="[
      'renderer-canvas-preview',
      { 'is-fullscreen': isFallbackFullscreen },
    ]"
  >
    <header class="renderer-canvas-preview__header">
      <div>
        <strong>画布区域</strong>
        <span>拖动节点，按 24px 网格吸附</span>
      </div>
      <div class="renderer-canvas-preview__actions" aria-label="画布操作">
        <button type="button" class="renderer-canvas-preview__action">
          <Share />
          <span>分享</span>
        </button>
        <button type="button" class="renderer-canvas-preview__action">
          <Star />
          <span>收藏</span>
        </button>
        <button type="button" class="renderer-canvas-preview__action" @click="toggleFullscreen">
          <FullScreen />
          <span>{{ isFullscreen ? '退出全屏' : '全屏' }}</span>
        </button>
      </div>
    </header>

    <div class="renderer-canvas-preview__surface">
      <AppFlowCanvas
        v-model:nodes="nodes"
        :node-types="nodeTypes"
        :fit-view-trigger="isFullscreen"
        v-bind="canvasCoreOptions"
      >
        <template #node-renderer="{ data }">
          <article class="renderer-canvas-node">
            <header>{{ (data as RendererNodeData).title }}</header>
            <div class="renderer-canvas-node__body">
              <ListMainLayout
                v-if="(data as RendererNodeData).renderer === 'list'"
                :schema="listSchema"
                :records="listRecords"
                :total="listRecords.length"
              />
              <FormMainLayout
                v-else-if="(data as RendererNodeData).renderer === 'form'"
                :schema="formSchema"
                readonly
              />
              <LineChartRenderer
                v-else
                v-bind="chartProps"
              />
            </div>
          </article>
        </template>
      </AppFlowCanvas>
    </div>
  </section>
</template>

<style scoped>
.renderer-canvas-preview {
  --renderer-canvas-node-width: 200px;
  --renderer-canvas-node-body-max-height: 150px;

  display: grid;
  gap: 10px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--chat-followup-border);
}

.renderer-canvas-preview:fullscreen,
.renderer-canvas-preview.is-fullscreen {
  --renderer-canvas-node-width: clamp(420px, 28vw, 520px);
  --renderer-canvas-node-body-max-height: none;

  grid-template-rows: auto minmax(0, 1fr);
  margin: 0;
  padding: 16px;
  border-top: 0;
  background: var(--chat-main-bg);
}

.renderer-canvas-preview:fullscreen {
  width: 100vw;
  height: 100vh;
}

.renderer-canvas-preview.is-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 3000;
}

.renderer-canvas-preview__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.renderer-canvas-preview__header div {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.renderer-canvas-preview__header strong {
  color: var(--chat-text-title);
  font-size: 14px;
}

.renderer-canvas-preview__header span {
  color: var(--chat-text-muted);
  font-size: 12px;
}

.renderer-canvas-preview__actions {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 6px;
}

.renderer-canvas-preview__action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  padding: 0 9px;
  border: 1px solid rgba(59, 130, 246, 0.28);
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.1);
  color: #93c5fd;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
  transition:
    border-color 0.16s ease,
    background 0.16s ease,
    color 0.16s ease;
}

.renderer-canvas-preview__action svg {
  width: 13px;
  height: 13px;
}

.renderer-canvas-preview__action:hover {
  border-color: rgba(96, 165, 250, 0.56);
  background: rgba(59, 130, 246, 0.18);
  color: #dbeafe;
}

.renderer-canvas-preview__surface {
  height: 460px;
  overflow: hidden;
  border: 1px solid var(--chat-panel-border);
  border-radius: 14px;
  background: var(--chat-main-bg);
}

.renderer-canvas-preview:fullscreen .renderer-canvas-preview__surface,
.renderer-canvas-preview.is-fullscreen .renderer-canvas-preview__surface {
  height: auto;
  min-height: 0;
  border-radius: 0;
}

.renderer-canvas-preview__surface :deep(.vue-flow__node-renderer) {
  width: var(--renderer-canvas-node-width);
  border: 0;
  background: transparent;
}

.renderer-canvas-node {
  overflow: hidden;
  border: 1px solid var(--chat-panel-border);
  border-radius: 12px;
  background: var(--chat-panel-bg);
  box-shadow: var(--chat-composer-shadow);
}

.renderer-canvas-node > header {
  padding: 9px 12px;
  border-bottom: 1px solid var(--chat-followup-border);
  color: var(--chat-text-title);
  font-size: 12px;
  font-weight: 700;
}

.renderer-canvas-node__body {
  width: var(--renderer-canvas-node-width);
  max-height: var(--renderer-canvas-node-body-max-height);
  overflow: auto;
  padding: 8px;
}

.renderer-canvas-preview:fullscreen .renderer-canvas-node__body,
.renderer-canvas-preview.is-fullscreen .renderer-canvas-node__body {
  overflow: visible;
  padding: 12px;
}

.renderer-canvas-node__body :deep(.list-main-layout) {
  min-height: 0;
  padding: 10px;
  border-radius: 10px;
}

.renderer-canvas-node__body :deep(.list-main-layout__body--tree) {
  display: block;
}

.renderer-canvas-node__body :deep(.list-main-layout__content-card) {
  gap: 10px;
  border-radius: 10px;
}

.renderer-canvas-node__body :deep(.list-filter-bar),
.renderer-canvas-node__body :deep(.list-tabs-bar),
.renderer-canvas-node__body :deep(.list-main-layout__pagination) {
  display: none;
}

.renderer-canvas-node__body :deep(.form-main-layout--workbench) {
  padding: 0;
}

.renderer-canvas-node__body :deep(.form-main-layout__groups) {
  gap: 10px;
}
</style>
