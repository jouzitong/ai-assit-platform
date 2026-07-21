<script setup lang="ts">
import {
  FullScreen,
  Minus,
  Plus,
  RefreshLeft,
} from '@element-plus/icons-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ResponsiveViewport } from '../../../application/layout'
import { loadRenderMetaContent, RenderJsonRuntimeHost } from '../../../application/runtime'
import RenderModeHost from '../../render/components/RenderModeHost.vue'
import {
  assertRenderModeAllowed,
  normalizeRenderRuntimeDocument,
  type RenderAppMode,
  type RenderRuntimeDocument,
} from '../../render/model/render-app'
import { findRenderMode } from '../../render/model/render-mode-registry'
import type { ChatArtifact } from '../types'
import { normalizeRenderArtifact, resolveRenderReferenceSize } from '../utils/renderArtifact'

const props = defineProps<{
  artifact: ChatArtifact
}>()

const workspaceRef = ref<HTMLElement | null>(null)
const scaleMultiplier = ref(1)
const actualScale = ref(0)
const fallbackFullscreen = ref(false)
const nativeFullscreen = ref(false)
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const renderDocument = ref<RenderRuntimeDocument | null>(null)
const renderMode = ref<RenderAppMode>('standard')
const referenceSize = computed(() => resolveRenderReferenceSize(renderDocument.value))
const modeDefinition = computed(() => findRenderMode(renderMode.value))
const usesHostViewport = computed(() => Boolean(modeDefinition.value?.usesResponsiveViewport))
const title = computed(() => (
  props.artifact.title
  || renderDocument.value?.presentation?.title
  || renderDocument.value?.title
  || props.artifact.artifactCode
  || props.artifact.codeRef
  || '生成页面'
))
const scaleLabel = computed(() => actualScale.value > 0
  ? `${Math.round(actualScale.value * 100)}%`
  : '适应中')
const isFullscreen = computed(() => nativeFullscreen.value || fallbackFullscreen.value)

let loadSequence = 0

watch(() => props.artifact, () => {
  scaleMultiplier.value = 1
  actualScale.value = 0
  void loadArtifactDocument()
}, { deep: false, immediate: true })

onMounted(() => {
  document.addEventListener('fullscreenchange', handleFullscreenChange)
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  if (document.fullscreenElement === workspaceRef.value) {
    void document.exitFullscreen()
  }
})

function zoomOut() {
  scaleMultiplier.value = Math.max(0.5, roundMultiplier(scaleMultiplier.value - 0.1))
}

function zoomIn() {
  scaleMultiplier.value = Math.min(3, roundMultiplier(scaleMultiplier.value + 0.1))
}

function resetZoom() {
  scaleMultiplier.value = 1
}

function handleScaleChange(payload: { scale: number }) {
  actualScale.value = payload.scale
}

async function toggleFullscreen() {
  if (document.fullscreenElement === workspaceRef.value) {
    await document.exitFullscreen()
    return
  }
  if (fallbackFullscreen.value) {
    fallbackFullscreen.value = false
    return
  }
  try {
    if (workspaceRef.value?.requestFullscreen) {
      await workspaceRef.value.requestFullscreen()
      return
    }
  } catch {
    // 浏览器拒绝原生全屏时使用页面级全屏兜底。
  }
  fallbackFullscreen.value = true
  await nextTick()
}

function handleFullscreenChange() {
  nativeFullscreen.value = document.fullscreenElement === workspaceRef.value
}

async function loadArtifactDocument() {
  const sequence = ++loadSequence
  const normalized = normalizeRenderArtifact(props.artifact)
  loading.value = false
  errorMessage.value = normalized.error
  renderDocument.value = normalized.document
  renderMode.value = normalized.reference?.layout || 'standard'

  if (!normalized.reference || normalized.error) {
    return
  }

  loading.value = true
  try {
    const content = await loadRenderMetaContent(normalized.reference.pageCode)
    if (!isRecord(content)) {
      throw new Error('Render Meta 返回的数据格式不正确')
    }
    const documentValue = normalizeRenderRuntimeDocument(content, normalized.reference.pageCode)
    assertRenderModeAllowed(normalized.reference.layout, documentValue.presentation)
    if (sequence !== loadSequence) {
      return
    }
    renderDocument.value = documentValue
  } catch (error) {
    if (sequence !== loadSequence) {
      return
    }
    errorMessage.value = error instanceof Error ? error.message : '生成页面加载失败'
  } finally {
    if (sequence === loadSequence) {
      loading.value = false
    }
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function roundMultiplier(value: number) {
  return Math.round(value * 10) / 10
}
</script>

<template>
  <section
    ref="workspaceRef"
    :class="['generated-artifact-workspace', { 'is-fallback-fullscreen': fallbackFullscreen }]"
    aria-label="生成页面预览"
  >
    <header class="generated-artifact-workspace__toolbar">
      <div class="generated-artifact-workspace__title" :title="title">
        <span class="generated-artifact-workspace__status" aria-hidden="true"></span>
        <strong>{{ title }}</strong>
      </div>

      <div class="generated-artifact-workspace__actions" aria-label="页面缩放工具">
        <button
          v-if="!usesHostViewport"
          type="button"
          aria-label="缩小生成页面"
          title="缩小"
          @click="zoomOut"
        >
          <el-icon><Minus /></el-icon>
        </button>
        <output
          v-if="!usesHostViewport"
          class="generated-artifact-workspace__scale"
          aria-live="polite"
        >{{ scaleLabel }}</output>
        <button
          v-if="!usesHostViewport"
          type="button"
          aria-label="放大生成页面"
          title="放大"
          @click="zoomIn"
        >
          <el-icon><Plus /></el-icon>
        </button>
        <button
          v-if="!usesHostViewport"
          type="button"
          aria-label="适应可用空间"
          title="适应可用空间"
          @click="resetZoom"
        >
          <el-icon><RefreshLeft /></el-icon>
        </button>
        <button
          type="button"
          :aria-label="isFullscreen ? '退出全屏' : '全屏查看'"
          :title="isFullscreen ? '退出全屏' : '全屏查看'"
          :aria-pressed="isFullscreen"
          @click="toggleFullscreen"
        >
          <el-icon><FullScreen /></el-icon>
        </button>
      </div>
    </header>

    <div class="generated-artifact-workspace__viewport">
      <RenderModeHost
        v-if="usesHostViewport"
        :mode="renderMode"
        :title="renderMode === 'standard' ? '' : title"
        :description="renderDocument?.presentation?.description"
        :loading="loading"
        :refreshable="false"
      >
        <div class="generated-artifact-workspace__canvas">
          <RenderJsonRuntimeHost
            :document="renderDocument"
            :loading="loading"
            :error="errorMessage"
          />
        </div>
      </RenderModeHost>
      <ResponsiveViewport
        v-else
        preset="chatArtifactPreview"
        :config="{ referenceSize }"
        :scale-multiplier="scaleMultiplier"
        @scale-change="handleScaleChange"
      >
        <RenderModeHost
          :mode="renderMode"
          :title="renderMode === 'standard' ? '' : title"
          :description="renderDocument?.presentation?.description"
          :loading="loading"
          :refreshable="false"
        >
          <div class="generated-artifact-workspace__canvas">
            <RenderJsonRuntimeHost
              :document="renderDocument"
              :loading="loading"
              :error="errorMessage"
            />
          </div>
        </RenderModeHost>
      </ResponsiveViewport>
    </div>
  </section>
</template>

<style scoped>
.generated-artifact-workspace {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  width: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 0.0625rem solid var(--chat-panel-border);
  border-radius: 0.625rem;
  background: var(--chat-main-bg);
}

.generated-artifact-workspace:fullscreen,
.generated-artifact-workspace.is-fallback-fullscreen {
  width: 100vw;
  height: 100vh;
  border: 0;
  border-radius: 0;
}

.generated-artifact-workspace.is-fallback-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 3000;
}

.generated-artifact-workspace__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  min-height: 2.75rem;
  gap: 0.75rem;
  padding: 0.5rem 0.75rem;
  border-bottom: 0.0625rem solid var(--chat-panel-border);
  background: var(--chat-panel-bg);
}

.generated-artifact-workspace__title,
.generated-artifact-workspace__actions {
  display: flex;
  align-items: center;
}

.generated-artifact-workspace__title {
  min-width: 0;
  gap: 0.5rem;
  color: var(--chat-text-primary);
  font-size: 0.875rem;
}

.generated-artifact-workspace__title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.generated-artifact-workspace__status {
  flex: none;
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
  background: var(--el-color-success);
}

.generated-artifact-workspace__actions {
  flex: none;
  gap: 0.25rem;
}

.generated-artifact-workspace__actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  padding: 0;
  border: 0;
  border-radius: 0.5rem;
  background: transparent;
  color: var(--chat-text-secondary);
  cursor: pointer;
}

.generated-artifact-workspace__actions button:hover {
  background: var(--chat-hover-bg);
  color: var(--chat-text-primary);
}

.generated-artifact-workspace__actions button:focus-visible {
  outline: 0.125rem solid var(--el-color-primary);
  outline-offset: 0.0625rem;
}

.generated-artifact-workspace__scale {
  min-width: 3.25rem;
  color: var(--chat-text-muted);
  font-size: 0.75rem;
  text-align: center;
}

.generated-artifact-workspace__viewport {
  display: flex;
  width: 100%;
  min-width: 0;
  min-height: 0;
  aspect-ratio: 1200 / 720;
  overflow: hidden;
  background: var(--chat-soft-bg);
}

.generated-artifact-workspace:fullscreen .generated-artifact-workspace__viewport,
.generated-artifact-workspace.is-fallback-fullscreen .generated-artifact-workspace__viewport {
  aspect-ratio: auto;
}

.generated-artifact-workspace__viewport :deep(.standard-mode-host),
.generated-artifact-workspace__viewport :deep(.dashboard-mode-host),
.generated-artifact-workspace__viewport :deep(.embedded-mode-host) {
  height: 100%;
  min-height: 0;
}

.generated-artifact-workspace__viewport :deep(.report-mode-host) {
  min-height: 100%;
}

.generated-artifact-workspace__canvas {
  width: 100%;
  height: 100%;
  padding: 1rem;
  overflow: hidden;
  box-sizing: border-box;
  border-radius: 0.75rem;
  background: var(--el-bg-color);
  box-shadow: 0 0.25rem 1rem rgb(0 0 0 / 8%);
}

@media (max-width: 60rem) {
  .generated-artifact-workspace__toolbar {
    flex-wrap: wrap;
  }
}

@media (max-width: 30rem) {
  .generated-artifact-workspace__toolbar {
    gap: 0.25rem;
    padding-inline: 0.5rem;
  }

  .generated-artifact-workspace__title {
    width: 100%;
  }

  .generated-artifact-workspace__actions {
    width: 100%;
    justify-content: flex-end;
  }
}

@media (prefers-reduced-motion: reduce) {
  .generated-artifact-workspace * {
    scroll-behavior: auto;
  }
}
</style>
