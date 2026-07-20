<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  loadRenderMetaContent,
  RenderJsonRuntimeHost,
  upsertRenderMetaContent,
  type RenderRuntimeNodeScope,
} from '../../../application/runtime'
import type { RendererAction } from '../../../application/renderers/list/types'
import { getDeveloperModeEnabled } from '../../../utils/developerMode'
import RenderDeveloperTools from '../components/RenderDeveloperTools.vue'
import RenderModeHost from '../components/RenderModeHost.vue'
import RenderRuntimeState from '../components/RenderRuntimeState.vue'
import {
  assertRenderModeAllowed,
  isRenderAppMode,
  normalizeRenderAppCode,
  normalizeRenderRuntimeDocument,
  type RenderAppMode,
  type RenderRuntimeDocument,
} from '../model/render-app'

const route = useRoute()
const loading = ref(false)
const errorMessage = ref('')
const renderMetaContent = ref<Record<string, unknown> | null>(null)
const renderDocument = ref<RenderRuntimeDocument | null>(null)
const renderMode = ref<RenderAppMode>('standard')
const renderCode = ref('')
const runtimeKey = ref(0)
const lastRefreshedAt = ref('')
const developerModeEnabled = ref(false)
const metadataVisible = ref(false)
const scopeVisible = ref(false)
const metadataDraft = ref('')
const metadataSaving = ref(false)
const scopeNodes = ref<Record<string, RenderRuntimeNodeScope>>({})

let loadSequence = 0
let refreshTimer: number | undefined

const RENDER_META_ACTION = '__render_meta__'
const RENDER_SCOPE_ACTION = '__render_scope__'

const presentation = computed(() => renderDocument.value?.presentation)
const pageTitle = computed(() => (
  presentation.value?.title
  || renderDocument.value?.title
  || renderCode.value
  || '动态应用'
))
const pageDescription = computed(() => presentation.value?.description)
const responsivePreset = computed(() => (
  presentation.value?.responsivePreset
  || (renderMode.value === 'dashboard' ? 'dashboard' : undefined)
))
const developerActions = computed<RendererAction[]>(() => developerModeEnabled.value
  ? [
      {
        key: RENDER_META_ACTION,
        name: '元数据配置',
        action: RENDER_META_ACTION,
        type: 'warning',
      },
      {
        key: RENDER_SCOPE_ACTION,
        name: '',
        title: 'SCOPE 上下文',
        action: RENDER_SCOPE_ACTION,
        icon: 'operation',
        type: 'info',
      },
    ]
  : [])
const hasIntegratedDeveloperTools = computed(() => (
  developerModeEnabled.value && containsListRenderer(renderDocument.value?.root)
))
const runtimeScope = computed(() => {
  const nodes = Object.values(scopeNodes.value)
  return {
    code: renderCode.value,
    mode: renderMode.value,
    document: renderDocument.value,
    schema: Object.fromEntries(nodes.map(node => [node.id, node.schema])),
    query: Object.fromEntries(nodes.map(node => [node.id, node.query])),
    params: {
      route: { ...route.params },
      query: { ...route.query },
    },
    requestPlans: nodes.flatMap(node => node.requestPlans.map(plan => ({
      nodeId: node.id,
      component: node.component,
      plan,
    }))),
    data: Object.fromEntries(nodes.map(node => [node.id, node.data])),
    state: {
      loading: loading.value,
      error: errorMessage.value || undefined,
      revision: renderDocument.value?.revision,
      lastRefreshedAt: lastRefreshedAt.value,
      nodes: Object.fromEntries(nodes.map(node => [node.id, node.state])),
    },
    events: nodes.flatMap(node => node.events.map(event => ({
      ...event,
      source: {
        nodeId: node.id,
        component: node.component,
      },
    }))),
    nodes: scopeNodes.value,
  }
})

watch(
  () => [route.params.mode, route.params.code],
  () => {
    void loadRuntimeDocument()
  },
  { immediate: true },
)

watch(pageTitle, (value) => {
  document.title = value ? `${value} - AI Conversation UI` : 'AI Conversation UI'
}, { immediate: true })

watch(
  [renderMode, () => presentation.value?.refreshInterval],
  configureAutoRefresh,
  { immediate: true },
)

onBeforeUnmount(() => {
  clearAutoRefresh()
})

onMounted(() => {
  developerModeEnabled.value = getDeveloperModeEnabled()
})

async function loadRuntimeDocument() {
  const sequence = ++loadSequence
  loading.value = true
  errorMessage.value = ''
  metadataVisible.value = false
  scopeVisible.value = false
  metadataDraft.value = ''
  renderMetaContent.value = null
  renderDocument.value = null
  scopeNodes.value = {}
  clearAutoRefresh()

  try {
    const modeValue = readRouteParam(route.params.mode)
    if (!isRenderAppMode(modeValue)) {
      throw new Error(`不支持的页面模式: ${modeValue || 'unknown'}`)
    }

    const code = normalizeRenderAppCode(readRouteParam(route.params.code))
    renderMode.value = modeValue
    renderCode.value = code

    const content = await loadRenderMetaContent(code)
    if (!isRecord(content)) {
      throw new Error('Render Meta 返回的数据格式不正确')
    }

    const documentValue = normalizeRenderRuntimeDocument(content, code)
    assertRenderModeAllowed(modeValue, documentValue.presentation)

    if (sequence !== loadSequence) {
      return
    }

    renderMetaContent.value = content
    renderDocument.value = documentValue
    refreshRuntime()
    configureAutoRefresh()
  } catch (error) {
    if (sequence !== loadSequence) {
      return
    }
    errorMessage.value = error instanceof Error ? error.message : '动态页面加载失败'
  } finally {
    if (sequence === loadSequence) {
      loading.value = false
    }
  }
}

function refreshRuntime() {
  if (!renderDocument.value) {
    return
  }
  scopeNodes.value = {}
  runtimeKey.value += 1
  lastRefreshedAt.value = new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date())
}

function handleRuntimeScopeChange(scope: RenderRuntimeNodeScope) {
  scopeNodes.value = {
    ...scopeNodes.value,
    [scope.id]: scope,
  }
}

function openMetadataEditor() {
  const content = renderMetaContent.value || createRenderMetaTemplate()
  metadataDraft.value = JSON.stringify(content, null, 2)
  metadataVisible.value = true
}

function handleDeveloperAction(action: RendererAction) {
  if (action.action === RENDER_META_ACTION) {
    openMetadataEditor()
    return
  }
  if (action.action === RENDER_SCOPE_ACTION) {
    scopeVisible.value = true
  }
}

function createRenderMetaTemplate(): Record<string, unknown> {
  const code = renderCode.value || 'render-app'
  return {
    protocol: 'render-json',
    protocolVersion: '1.0.0',
    pageId: code,
    presentation: {
      title: code,
      defaultMode: renderMode.value,
      allowedModes: [renderMode.value],
    },
    root: {
      id: `${code}-root`,
      component: 'text',
      props: {
        text: '请编辑 Render JSON 元数据',
      },
    },
  }
}

async function saveMetadata(content: Record<string, unknown>) {
  if (!renderCode.value || metadataSaving.value) {
    return
  }

  metadataSaving.value = true
  try {
    const draftDocument = normalizeRenderRuntimeDocument(content, renderCode.value)
    assertRenderModeAllowed(renderMode.value, draftDocument.presentation)
    const saved = await upsertRenderMetaContent(renderCode.value, content)
    const savedContent = isRecord(saved) ? saved : content
    const documentValue = savedContent === content
      ? draftDocument
      : normalizeRenderRuntimeDocument(savedContent, renderCode.value)
    if (documentValue !== draftDocument) {
      assertRenderModeAllowed(renderMode.value, documentValue.presentation)
    }
    renderMetaContent.value = savedContent
    renderDocument.value = documentValue
    metadataDraft.value = JSON.stringify(savedContent, null, 2)
    metadataVisible.value = false
    refreshRuntime()
    configureAutoRefresh()
    ElMessage.success('Render JSON 元数据已保存并重新渲染')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Render JSON 元数据保存失败')
  } finally {
    metadataSaving.value = false
  }
}

function configureAutoRefresh() {
  clearAutoRefresh()
  const interval = presentation.value?.refreshInterval
  if (renderMode.value !== 'dashboard' || !interval || !renderDocument.value) {
    return
  }

  refreshTimer = window.setInterval(
    refreshRuntime,
    Math.max(5, interval) * 1000,
  )
}

function clearAutoRefresh() {
  if (refreshTimer !== undefined) {
    window.clearInterval(refreshTimer)
    refreshTimer = undefined
  }
}

function readRouteParam(value: unknown) {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : ''
  }
  return typeof value === 'string' ? value : ''
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function containsListRenderer(node: unknown): boolean {
  if (!isRecord(node)) {
    return false
  }
  const component = typeof node.component === 'string' ? node.component.toLowerCase() : ''
  if ([
    'zg-list-main-layout',
    'list-main-layout',
    'zg-common-list',
    'zg-common-tree-list',
    'common-list',
    'common-tree-list',
  ].includes(component)) {
    return true
  }
  return Array.isArray(node.children) && node.children.some(containsListRenderer)
}
</script>

<template>
  <RenderModeHost
    :mode="renderMode"
    :title="renderMode === 'standard' ? undefined : pageTitle"
    :description="renderMode === 'standard' ? undefined : pageDescription"
    :loading="loading"
    :refreshable="Boolean(renderDocument)"
    :last-refreshed-at="lastRefreshedAt"
    :responsive-preset="responsivePreset"
    @refresh="refreshRuntime"
  >
    <RenderRuntimeState
      v-if="loading || errorMessage"
      :loading="loading"
      :error="errorMessage"
      @retry="loadRuntimeDocument"
    />
    <RenderJsonRuntimeHost
      v-else-if="renderDocument"
      :key="runtimeKey"
      :document="renderDocument"
      :observe="developerModeEnabled"
      :developer-actions="developerActions"
      @scope-change="handleRuntimeScopeChange"
      @developer-action="handleDeveloperAction"
    />
  </RenderModeHost>

  <RenderDeveloperTools
    v-if="developerModeEnabled"
    v-model:metadata-visible="metadataVisible"
    v-model:scope-visible="scopeVisible"
    v-model:metadata-draft="metadataDraft"
    :code="renderCode"
    :metadata-saving="metadataSaving"
    :scope="runtimeScope"
    :show-triggers="!hasIntegratedDeveloperTools"
    @open-metadata="openMetadataEditor"
    @save="saveMetadata"
  />
</template>
