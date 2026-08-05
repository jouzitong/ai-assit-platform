<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { FullScreen, Share, Star } from '@element-plus/icons-vue'
import AppDashboardCanvas from '../../../components/canvas/AppDashboardCanvas/index.vue'
import ListMainLayout from '../../../application/renderers/list/ListMainLayout.vue'
import FormMainLayout from '../../../application/renderers/form/FormMainLayout.vue'
import LineChartRenderer from '../../../application/renderers/echarts/LineChartRenderer.vue'
import type { DashboardCanvasItem } from '../../../components/canvas'
import type { FormRendererSchema } from '../../../application/renderers/form/types'
import type { ListRendererSchema } from '../../../application/renderers/list/types'

const previewRef = ref<HTMLElement | null>(null)
const isFullscreen = ref(false)
const isFallbackFullscreen = ref(false)
const previousBodyOverflow = ref<string | null>(null)

const layout = ref<DashboardCanvasItem[]>([
  { id: 'risk-list', title: '异常任务列表', x: 0, y: 0, w: 6, h: 4, minW: 4, minH: 3 },
  { id: 'plan-form', title: '处置计划', x: 6, y: 0, w: 6, h: 4, minW: 4, minH: 3 },
  { id: 'trend-chart', title: '异常趋势', x: 2, y: 4, w: 8, h: 4, minW: 5, minH: 3 },
])

const listSchema: ListRendererSchema = {
  id: 'dashboard-list-demo',
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
    { key: 'name', name: '任务', field: ['name'] },
    { key: 'owner', name: '负责人', field: ['owner'] },
    { key: 'status', name: '状态', field: ['status'] },
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
  id: 'dashboard-form-demo',
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
  height: isFullscreen.value ? 320 : 190,
  series: [
    { name: '登录失败率', data: [2.1, 2.8, 3.7, 3.1, 2.6], area: true },
    { name: '提现拦截率', data: [1.2, 1.8, 2.4, 2.9, 2.2] },
  ],
}))

const syncFullscreenState = () => {
  if (isFallbackFullscreen.value) {
    return
  }
  isFullscreen.value = document.fullscreenElement === document.documentElement
}

const enterFallbackFullscreen = () => {
  if (previousBodyOverflow.value === null) {
    previousBodyOverflow.value = document.body.style.overflow
  }
  document.body.style.overflow = 'hidden'
  isFallbackFullscreen.value = true
  isFullscreen.value = true
}

const exitFallbackFullscreen = () => {
  isFallbackFullscreen.value = false
  isFullscreen.value = false
  if (previousBodyOverflow.value !== null) {
    document.body.style.overflow = previousBodyOverflow.value
    previousBodyOverflow.value = null
  }
}

const toggleFullscreen = async () => {
  if (isFallbackFullscreen.value) {
    exitFallbackFullscreen()
    return
  }

  enterFallbackFullscreen()
}

onMounted(() => {
  document.addEventListener('fullscreenchange', syncFullscreenState)
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', syncFullscreenState)
  exitFallbackFullscreen()
})
</script>

<template>
  <section
    ref="previewRef"
    :class="[
      'dashboard-canvas-preview',
      { 'is-fullscreen': isFullscreen },
    ]"
  >
    <header class="dashboard-canvas-preview__header">
      <div>
        <strong>看板画布</strong>
        <span>拖动标题移动组件，拖动右下角调整尺寸</span>
      </div>
      <div class="dashboard-canvas-preview__actions" aria-label="看板操作">
        <button type="button" class="dashboard-canvas-preview__action">
          <Share />
          <span>分享</span>
        </button>
        <button type="button" class="dashboard-canvas-preview__action">
          <Star />
          <span>收藏</span>
        </button>
        <button type="button" class="dashboard-canvas-preview__action" @click="toggleFullscreen">
          <FullScreen />
          <span>{{ isFullscreen ? '退出全屏' : '全屏' }}</span>
        </button>
      </div>
    </header>

    <div class="dashboard-canvas-preview__surface">
      <AppDashboardCanvas
        v-model:layout="layout"
        :columns="12"
        :row-height="isFullscreen ? 96 : 56"
        :gap="12"
        :min-rows="isFullscreen ? 9 : 7"
      >
        <template #widget="{ item }">
          <ListMainLayout
            v-if="item.id === 'risk-list'"
            :schema="listSchema"
            :records="listRecords"
            :total="listRecords.length"
          />
          <FormMainLayout
            v-else-if="item.id === 'plan-form'"
            :schema="formSchema"
            readonly
          />
          <LineChartRenderer
            v-else
            v-bind="chartProps"
          />
        </template>
      </AppDashboardCanvas>
    </div>
  </section>
</template>

<style scoped>
.dashboard-canvas-preview {
  display: grid;
  gap: 10px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--chat-followup-border);
}

.dashboard-canvas-preview:fullscreen,
.dashboard-canvas-preview.is-fullscreen {
  grid-template-rows: auto minmax(0, 1fr);
  margin: 0;
  padding: 16px;
  border-top: 0;
  background: var(--chat-main-bg);
}

.dashboard-canvas-preview:fullscreen {
  width: 100vw;
  height: 100vh;
}

.dashboard-canvas-preview.is-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 3000;
}

.dashboard-canvas-preview__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.dashboard-canvas-preview__header div {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.dashboard-canvas-preview__header strong {
  color: var(--chat-text-title);
  font-size: 14px;
}

.dashboard-canvas-preview__header span {
  color: var(--chat-text-muted);
  font-size: 12px;
}

.dashboard-canvas-preview__actions {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 6px;
}

.dashboard-canvas-preview__action {
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
}

.dashboard-canvas-preview__action svg {
  width: 13px;
  height: 13px;
}

.dashboard-canvas-preview__surface {
  height: 520px;
  overflow: hidden;
  border: 1px solid var(--chat-panel-border);
  border-radius: 14px;
  background: var(--chat-main-bg);
}

.dashboard-canvas-preview:fullscreen .dashboard-canvas-preview__surface,
.dashboard-canvas-preview.is-fullscreen .dashboard-canvas-preview__surface {
  height: auto;
  min-height: 0;
  border-radius: 0;
}

.dashboard-canvas-preview__surface :deep(.list-main-layout) {
  min-height: 0;
  padding: 10px;
  border-radius: 8px;
}

.dashboard-canvas-preview__surface :deep(.list-main-layout__body--tree) {
  display: block;
}

.dashboard-canvas-preview__surface :deep(.list-main-layout__content-card) {
  gap: 10px;
  border-radius: 8px;
}

.dashboard-canvas-preview__surface :deep(.list-filter-bar),
.dashboard-canvas-preview__surface :deep(.list-tabs-bar),
.dashboard-canvas-preview__surface :deep(.list-main-layout__pagination) {
  display: none;
}

.dashboard-canvas-preview__surface :deep(.form-main-layout--workbench) {
  padding: 0;
}
</style>
