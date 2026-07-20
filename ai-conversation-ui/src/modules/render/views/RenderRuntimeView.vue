<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  loadRenderMetaContent,
  RenderJsonRuntimeHost,
} from '../../../application/runtime'
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
const renderDocument = ref<RenderRuntimeDocument | null>(null)
const renderMode = ref<RenderAppMode>('standard')
const renderCode = ref('')
const runtimeKey = ref(0)
const lastRefreshedAt = ref('')

let loadSequence = 0
let refreshTimer: number | undefined

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

async function loadRuntimeDocument() {
  const sequence = ++loadSequence
  loading.value = true
  errorMessage.value = ''
  renderDocument.value = null
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
  runtimeKey.value += 1
  lastRefreshedAt.value = new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date())
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
</script>

<template>
  <RenderModeHost
    :mode="renderMode"
    :title="pageTitle"
    :description="pageDescription"
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
    />
  </RenderModeHost>
</template>
