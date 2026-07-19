<script setup lang="ts">
import {
  Close,
  EditPen,
  FullScreen,
  Memo,
  Picture,
  ScaleToOriginal,
  Tickets,
  View,
} from '@element-plus/icons-vue'
import { computed, nextTick, ref, watch } from 'vue'
import { useResponsiveOverlayTarget } from '../../../composables/useResponsiveViewport'
import AppFieldShell from '../shared/AppFieldShell.vue'
import CodeEditorSurface from './CodeEditorSurface.vue'
import MarkdownPreview from './MarkdownPreview.vue'
import type {
  AppCodeEditorFormat,
  AppCodeEditorFormatOption,
  AppCodeEditorMarkdownMode,
  AppCodeEditorStatus,
} from './types'

defineOptions({
  inheritAttrs: false,
})

const DEFAULT_FORMAT_OPTIONS: AppCodeEditorFormatOption[] = [
  { label: 'JSON', value: 'json' },
  { label: 'Python', value: 'python' },
  { label: 'JavaScript', value: 'javascript' },
  { label: 'Markdown', value: 'markdown' },
  { label: 'AsciiDoc', value: 'asciidoc' },
  { label: 'Text', value: 'text' },
]

const props = withDefaults(
  defineProps<{
    modelValue?: string
    format?: AppCodeEditorFormat
    markdownMode?: AppCodeEditorMarkdownMode
    label?: string
    hint?: string
    error?: string
    placeholder?: string
    disabled?: boolean
    readonly?: boolean
    required?: boolean
    block?: boolean
    height?: string
    minHeight?: string
    maxRows?: number
    showToolbar?: boolean
    showFormatSwitcher?: boolean
    showMarkdownModeSwitcher?: boolean
    expandable?: boolean
    expandTitle?: string
    expandWidth?: string
    expandHeight?: string
    expandInitiallyFullscreen?: boolean
    toolbarLabel?: string
    formats?: AppCodeEditorFormatOption[]
    labelPosition?: 'left' | 'inner'
  }>(),
  {
    modelValue: '',
    format: 'text',
    markdownMode: 'edit',
    placeholder: '',
    disabled: false,
    readonly: false,
    required: false,
    block: true,
    height: '',
    minHeight: '320px',
    maxRows: 0,
    showToolbar: true,
    showFormatSwitcher: true,
    showMarkdownModeSwitcher: true,
    expandable: false,
    expandTitle: '放大编辑',
    expandWidth: 'var(--app-dialog-width-lg)',
    expandHeight: 'var(--app-layout-dialog-body-max-height)',
    expandInitiallyFullscreen: false,
    labelPosition: 'inner',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:format': [value: AppCodeEditorFormat]
  'update:markdownMode': [value: AppCodeEditorMarkdownMode]
  change: [value: string]
  'format-change': [value: AppCodeEditorFormat]
  'markdown-mode-change': [value: AppCodeEditorMarkdownMode]
}>()

const selectedMarkdownMode = ref<AppCodeEditorMarkdownMode>(props.markdownMode)
const responsiveOverlayTarget = useResponsiveOverlayTarget()
const inlineStatus = ref<AppCodeEditorStatus>({ checking: true, diagnostics: 0 })
const expandedStatus = ref<AppCodeEditorStatus>({ checking: true, diagnostics: 0 })
const expandedVisible = ref(false)
const expandedFullscreen = ref(false)
const expandedPreview = ref(false)
const expandedDraft = ref('')
const expandedEditorRef = ref<{ focus: () => void } | null>(null)

const selectedFormat = computed({
  get: () => props.format,
  set: (value: AppCodeEditorFormat) => {
    emit('update:format', value)
    emit('format-change', value)
  },
})

const isMarkdown = computed(() => props.format === 'markdown')
const showEditorSurface = computed(() => !isMarkdown.value || selectedMarkdownMode.value !== 'preview')
const showMarkdownPreview = computed(() => isMarkdown.value && selectedMarkdownMode.value !== 'edit')
const showExpandedPreview = computed(() => isMarkdown.value && expandedPreview.value)

const toolbarFormats = computed(() => (props.formats?.length ? props.formats : DEFAULT_FORMAT_OPTIONS)
  .filter(option => DEFAULT_FORMAT_OPTIONS.some(item => item.value === option.value)))

const formatLabel = computed(() => toolbarFormats.value.find(item => item.value === props.format)?.label || props.format)

const diagnosticsSummary = computed(() => {
  if (inlineStatus.value.checking) {
    return '检查中...'
  }
  if (inlineStatus.value.diagnostics === 0) {
    return '语法正常'
  }
  return `发现 ${inlineStatus.value.diagnostics} 个语法问题`
})

const diagnosticsType = computed(() => {
  if (inlineStatus.value.checking) {
    return 'info'
  }
  return inlineStatus.value.diagnostics > 0 ? 'danger' : 'success'
})

const expandedDiagnosticsSummary = computed(() => {
  if (expandedStatus.value.checking) {
    return '检查中...'
  }
  if (expandedStatus.value.diagnostics === 0) {
    return '语法正常'
  }
  return `发现 ${expandedStatus.value.diagnostics} 个语法问题`
})

const expandedDiagnosticsType = computed(() => {
  if (expandedStatus.value.checking) {
    return 'info'
  }
  return expandedStatus.value.diagnostics > 0 ? 'danger' : 'success'
})

const editorMaxHeight = computed(() => {
  if (!props.maxRows || props.maxRows <= 0) {
    return ''
  }
  return `calc(${props.maxRows} * 1.65em + 28px)`
})

function handleMarkdownModeChange(mode: string | number | boolean | undefined) {
  if (mode !== 'edit' && mode !== 'split' && mode !== 'preview') {
    return
  }
  selectedMarkdownMode.value = mode
  emit('update:markdownMode', mode)
  emit('markdown-mode-change', mode)
}

function handleInlineChange(value: string) {
  emit('update:modelValue', value)
  emit('change', value)
}

function openExpandedEditor() {
  if (!props.expandable || props.disabled) {
    return
  }
  expandedDraft.value = props.modelValue
  expandedFullscreen.value = props.expandInitiallyFullscreen
  expandedPreview.value = isMarkdown.value && selectedMarkdownMode.value === 'preview'
  expandedVisible.value = true
}

function cancelExpandedEditor() {
  expandedVisible.value = false
}

function confirmExpandedEditor() {
  if (expandedDraft.value !== props.modelValue) {
    emit('update:modelValue', expandedDraft.value)
    emit('change', expandedDraft.value)
  }
  expandedVisible.value = false
}

function resetExpandedEditor() {
  expandedDraft.value = ''
  expandedFullscreen.value = false
  expandedPreview.value = false
  expandedStatus.value = { checking: true, diagnostics: 0 }
}

function focusExpandedEditor() {
  expandedEditorRef.value?.focus()
}

function toggleExpandedFullscreen() {
  expandedFullscreen.value = !expandedFullscreen.value
}

function toggleExpandedPreview() {
  expandedPreview.value = !expandedPreview.value
  if (!expandedPreview.value) {
    nextTick(focusExpandedEditor)
  }
}

function handleExpandedOpened() {
  if (!showExpandedPreview.value) {
    focusExpandedEditor()
  }
}

watch(
  () => props.markdownMode,
  value => {
    selectedMarkdownMode.value = value
  },
)
</script>

<template>
  <AppFieldShell
    :label="label"
    :hint="hint"
    :error="error"
    :block="block"
    :required="required"
    :label-position="labelPosition"
    class="app-code-editor-shell"
  >
    <div class="app-code-editor" :class="{ 'app-code-editor--disabled': disabled }">
      <div v-if="showToolbar" class="app-code-editor__toolbar">
        <div class="app-code-editor__toolbar-left">
          <el-select
            v-if="showFormatSwitcher"
            v-model="selectedFormat"
            size="small"
            class="app-code-editor__format"
            :disabled="disabled || readonly"
            :append-to="responsiveOverlayTarget"
          >
            <el-option
              v-for="item in toolbarFormats"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <span v-else-if="toolbarLabel" class="app-code-editor__toolbar-label">
            {{ toolbarLabel }}
          </span>
          <el-radio-group
            v-if="isMarkdown && showMarkdownModeSwitcher"
            :model-value="selectedMarkdownMode"
            size="small"
            class="app-code-editor__markdown-modes"
            :disabled="disabled"
            aria-label="Markdown 显示模式"
            @update:model-value="handleMarkdownModeChange"
          >
            <el-radio-button value="edit" aria-label="文本模式" title="文本模式">
              <el-icon aria-hidden="true"><Tickets /></el-icon>
            </el-radio-button>
            <el-radio-button value="split" aria-label="分屏模式" title="分屏模式">
              <el-icon aria-hidden="true"><Memo /></el-icon>
            </el-radio-button>
            <el-radio-button value="preview" aria-label="预览模式" title="预览模式">
              <el-icon aria-hidden="true"><Picture /></el-icon>
            </el-radio-button>
          </el-radio-group>
        </div>
        <div class="app-code-editor__toolbar-right">
          <el-button
            v-if="expandable"
            text
            circle
            :icon="FullScreen"
            :disabled="disabled"
            aria-label="放大编辑"
            aria-haspopup="dialog"
            title="放大编辑"
            @click="openExpandedEditor"
          />
          <el-tag size="small" effect="plain" :type="diagnosticsType">
            {{ diagnosticsSummary }}
          </el-tag>
        </div>
      </div>
      <div
        class="app-code-editor__workspace"
        :class="{
          'app-code-editor__workspace--split': isMarkdown && selectedMarkdownMode === 'split',
          'app-code-editor__workspace--preview': isMarkdown && selectedMarkdownMode === 'preview',
        }"
      >
        <div v-show="showEditorSurface" class="app-code-editor__surface">
          <CodeEditorSurface
            :model-value="modelValue"
            :format="format"
            :placeholder="placeholder"
            :disabled="disabled"
            :readonly="readonly || expandable"
            height="100%"
            min-height="100%"
            max-height="none"
            @update:model-value="handleInlineChange"
            @status-change="inlineStatus = $event"
          />
          <button
            v-if="expandable"
            type="button"
            class="app-code-editor__expand-trigger"
            :disabled="disabled"
            aria-label="打开大型编辑框"
            aria-haspopup="dialog"
            @click="openExpandedEditor"
          >
            <span>
              <el-icon><FullScreen /></el-icon>
              点击放大编辑
            </span>
          </button>
        </div>
        <MarkdownPreview
          v-if="showMarkdownPreview"
          :content="modelValue"
          class="app-code-editor__preview"
        />
      </div>
    </div>
  </AppFieldShell>

  <el-dialog
    v-model="expandedVisible"
    class="app-code-editor-expand-dialog"
    modal-class="app-code-editor-expand-dialog-mask"
    :width="expandWidth"
    :fullscreen="expandedFullscreen"
    :append-to="responsiveOverlayTarget"
    :close-on-click-modal="false"
    :show-close="false"
    destroy-on-close
    @opened="handleExpandedOpened"
    @closed="resetExpandedEditor"
  >
    <template #header>
      <div class="app-code-editor__expand-header">
        <div class="app-code-editor__expand-heading">
          <strong>{{ expandTitle }}</strong>
          <span>{{ formatLabel }}</span>
        </div>
        <div class="app-code-editor__expand-actions">
          <el-tag size="small" effect="plain" :type="expandedDiagnosticsType">
            {{ expandedDiagnosticsSummary }}
          </el-tag>
          <el-button
            v-if="isMarkdown"
            text
            circle
            :icon="showExpandedPreview ? EditPen : View"
            :aria-label="showExpandedPreview ? '返回编辑' : '预览 Markdown'"
            :aria-pressed="showExpandedPreview"
            :title="showExpandedPreview ? '返回编辑' : '预览 Markdown'"
            @click="toggleExpandedPreview"
          />
          <el-button
            text
            circle
            :icon="expandedFullscreen ? ScaleToOriginal : FullScreen"
            :aria-label="expandedFullscreen ? '还原编辑框' : '全屏编辑'"
            :title="expandedFullscreen ? '还原窗口' : '全屏编辑'"
            @click="toggleExpandedFullscreen"
          />
          <el-button
            text
            circle
            :icon="Close"
            aria-label="关闭编辑框"
            title="关闭"
            @click="cancelExpandedEditor"
          />
        </div>
      </div>
    </template>

    <div
      class="app-code-editor__expand-body"
      :style="{ height: expandedFullscreen ? '100%' : expandHeight }"
    >
      <CodeEditorSurface
        ref="expandedEditorRef"
        v-show="!showExpandedPreview"
        v-model="expandedDraft"
        :format="format"
        :placeholder="placeholder"
        :readonly="readonly"
        height="100%"
        min-height="100%"
        max-height="none"
        @status-change="expandedStatus = $event"
      />
      <MarkdownPreview
        v-if="showExpandedPreview"
        :content="expandedDraft"
        class="app-code-editor__expand-preview"
      />
    </div>

    <template #footer>
      <div class="app-code-editor__expand-footer">
        <el-button @click="cancelExpandedEditor">取消</el-button>
        <el-button type="primary" @click="confirmExpandedEditor">
          {{ readonly ? '完成' : '确定' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.app-code-editor-shell {
  --app-code-editor-height: v-bind('props.height || "100%"');
  --app-code-editor-min-height: v-bind('props.minHeight');
  --app-code-editor-max-height: v-bind('editorMaxHeight || "none"');
  --app-code-editor-border: var(--app-editor-border);
  --app-code-editor-border-soft: var(--app-editor-border-soft);
  --app-code-editor-surface: var(--app-editor-surface);
  --app-code-editor-surface-strong: var(--app-editor-surface-strong);
  --app-code-editor-toolbar: var(--app-editor-toolbar);
  --app-code-editor-gutter-bg: var(--app-editor-gutter-bg);
  --app-code-editor-gutter-text: var(--app-editor-gutter-text);
  --app-code-editor-active-line: var(--app-editor-active-line);
  --app-code-editor-active-gutter: var(--app-editor-active-gutter);
  --app-code-editor-text: var(--app-editor-text);
  --app-code-editor-placeholder: var(--app-editor-placeholder);
  --app-code-editor-label: var(--app-editor-label);
  --app-code-editor-focus: var(--app-editor-focus);
  min-width: 0;
  max-width: 100%;
  container: app-code-editor / inline-size;
}

.app-code-editor {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  border: 1px solid var(--app-code-editor-border);
  border-radius: var(--app-radius-comfortable);
  background: linear-gradient(180deg, var(--app-code-editor-surface-strong) 0%, var(--app-code-editor-surface) 100%);
  overflow: hidden;
  min-width: 0;
  max-width: 100%;
}

.app-code-editor--disabled {
  opacity: 0.72;
}

.app-code-editor__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-3);
  min-height: 46px;
  padding: 0 var(--app-space-3);
  border-bottom: 1px solid var(--app-code-editor-border-soft);
  background: var(--app-code-editor-toolbar);
}

.app-code-editor__toolbar-left,
.app-code-editor__toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--app-space-2);
  min-width: 0;
}

.app-code-editor__format {
  flex: 0 0 160px;
  width: 160px;
  min-width: 160px;
}

.app-code-editor__format :deep(.el-select__wrapper) {
  min-width: 160px;
}

.app-code-editor__format :deep(.el-select__selected-item),
.app-code-editor__format :deep(.el-select__placeholder) {
  white-space: nowrap;
}

.app-code-editor__markdown-modes {
  --app-code-editor-mode-button-size: calc(var(--app-control-height-sm) - var(--app-space-tight));
  --el-radio-button-checked-bg-color: var(--app-accent-bg-strong);
  --el-radio-button-checked-text-color: var(--app-accent);
  --el-radio-button-checked-border-color: transparent;
  display: inline-flex;
  gap: var(--app-space-1);
  padding: var(--app-space-hairline);
  border: 1px solid var(--app-code-editor-border-soft);
  border-radius: var(--app-radius-control);
  background: var(--app-surface-muted);
  flex: 0 0 auto;
}

.app-code-editor__markdown-modes :deep(.el-radio-button__inner) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--app-code-editor-mode-button-size);
  min-width: var(--app-code-editor-mode-button-size);
  height: var(--app-code-editor-mode-button-size);
  padding: 0;
  border: 0;
  border-radius: var(--app-radius-md);
  outline: 0;
  background: transparent;
  color: var(--app-text-muted);
  box-shadow: none;
}

.app-code-editor__markdown-modes :deep(.el-radio-button__inner:hover) {
  background: var(--app-surface-solid);
  color: var(--app-code-editor-text);
}

.app-code-editor__markdown-modes :deep(.el-radio-button.is-active .el-radio-button__inner) {
  border-radius: var(--app-radius-md);
  box-shadow: none;
}

.app-code-editor__markdown-modes :deep(.el-radio-button__original-radio:focus-visible + .el-radio-button__inner) {
  border: 0;
  border-radius: var(--app-radius-md);
  outline: 2px solid var(--app-code-editor-focus);
  outline-offset: 1px;
}

.app-code-editor__markdown-modes :deep(.el-icon) {
  width: var(--app-font-size-title-sm);
  height: var(--app-font-size-title-sm);
  font-size: var(--app-font-size-title-sm);
}

.app-code-editor__toolbar-label {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 var(--app-space-compact);
  border: 1px solid var(--app-code-editor-border);
  border-radius: var(--app-radius-control);
  background: var(--app-code-editor-surface);
  color: var(--app-code-editor-label);
  font-size: var(--app-font-size-caption);
  line-height: 1;
}

.app-code-editor__workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  min-height: var(--app-code-editor-min-height);
  height: var(--app-code-editor-height);
  max-height: var(--app-code-editor-max-height);
  min-width: 0;
}

.app-code-editor__workspace--split {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.app-code-editor__surface {
  position: relative;
  min-width: 0;
  min-height: var(--app-code-editor-min-height);
  height: 100%;
  max-height: var(--app-code-editor-max-height);
}

.app-code-editor__expand-trigger {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  padding: var(--app-space-3);
  border: 0;
  background: transparent;
  color: var(--app-accent);
  cursor: zoom-in;
}

.app-code-editor__expand-trigger span {
  display: inline-flex;
  align-items: center;
  gap: var(--app-space-tight);
  min-height: var(--app-control-height-sm);
  padding: 0 var(--app-space-3);
  border: 1px solid var(--app-accent-border);
  border-radius: var(--app-radius-control);
  background: var(--app-surface-solid);
  box-shadow: var(--app-shadow-sm);
  font-size: var(--app-font-size-caption);
  font-weight: 600;
  opacity: 0;
  transition: opacity 180ms ease, background-color 180ms ease;
}

.app-code-editor__expand-trigger:hover {
  background: var(--app-accent-bg);
}

.app-code-editor__expand-trigger:hover span,
.app-code-editor__expand-trigger:focus-visible span {
  opacity: 1;
}

.app-code-editor__expand-trigger:focus-visible {
  outline: 2px solid var(--app-code-editor-focus);
  outline-offset: -2px;
}

.app-code-editor__expand-trigger:disabled {
  cursor: not-allowed;
}

.app-code-editor__expand-header,
.app-code-editor__expand-actions,
.app-code-editor__expand-footer {
  display: flex;
  align-items: center;
}

.app-code-editor__expand-header {
  justify-content: space-between;
  gap: var(--app-space-4);
  min-width: 0;
}

.app-code-editor__expand-heading {
  display: grid;
  gap: var(--app-space-1);
  min-width: 0;
}

.app-code-editor__expand-heading strong {
  color: var(--app-title);
  font-size: var(--app-font-size-title-md);
}

.app-code-editor__expand-heading span {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.app-code-editor__expand-actions {
  justify-content: flex-end;
  gap: var(--app-space-2);
  flex: 0 0 auto;
}

.app-code-editor__expand-body {
  --app-code-editor-min-height: 100%;
  --app-code-editor-max-height: none;
  --app-code-editor-border: var(--app-editor-border);
  --app-code-editor-border-soft: var(--app-editor-border-soft);
  --app-code-editor-surface: var(--app-editor-surface);
  --app-code-editor-surface-strong: var(--app-editor-surface-strong);
  --app-code-editor-gutter-bg: var(--app-editor-gutter-bg);
  --app-code-editor-gutter-text: var(--app-editor-gutter-text);
  --app-code-editor-active-line: var(--app-editor-active-line);
  --app-code-editor-active-gutter: var(--app-editor-active-gutter);
  --app-code-editor-text: var(--app-editor-text);
  --app-code-editor-placeholder: var(--app-editor-placeholder);
  --app-code-editor-focus: var(--app-editor-focus);
  display: grid;
  min-width: 0;
  min-height: calc(var(--app-control-height-lg) * 6);
  overflow: hidden;
  border: 1px solid var(--app-code-editor-border);
  border-radius: var(--app-radius-comfortable);
  background: var(--app-code-editor-surface);
}

.app-code-editor__expand-body > * {
  grid-area: 1 / 1;
  min-width: 0;
  min-height: 0;
}

.app-code-editor__expand-preview {
  height: 100%;
}

.app-code-editor__expand-footer {
  justify-content: flex-end;
  gap: var(--app-space-3);
}

:global(.app-code-editor-expand-dialog.el-dialog) {
  max-width: calc(100% - var(--app-space-8));
}

:global(.app-code-editor-expand-dialog.el-dialog.is-fullscreen) {
  display: flex;
  flex-direction: column;
  max-width: none;
}

:global(.app-code-editor-expand-dialog.el-dialog.is-fullscreen .el-dialog__body) {
  display: flex;
  flex: 1;
  min-height: 0;
}

:global(.app-code-editor-expand-dialog.el-dialog.is-fullscreen .app-code-editor__expand-body) {
  flex: 1;
  width: 100%;
  min-height: 0;
}

.app-code-editor__workspace--split .app-code-editor__preview {
  border-inline-start: 1px solid var(--app-code-editor-border-soft);
}

@container app-code-editor (max-width: 720px) {
  .app-code-editor__workspace--split {
    grid-template-columns: minmax(0, 1fr);
    height: auto;
    max-height: none;
  }

  .app-code-editor__workspace--split .app-code-editor__preview {
    border-inline-start: 0;
    border-top: 1px solid var(--app-code-editor-border-soft);
  }
}

@container app-code-editor (max-width: 420px) {
  .app-code-editor__toolbar {
    align-items: stretch;
    flex-direction: column;
    padding: var(--app-space-2) var(--app-space-3);
  }

  .app-code-editor__format,
  .app-code-editor__format :deep(.el-select__wrapper) {
    width: 100%;
    min-width: 0;
  }

  .app-code-editor__format {
    flex: 1 1 100%;
  }

  .app-code-editor__toolbar-left,
  .app-code-editor__toolbar-right {
    width: 100%;
  }

  .app-code-editor__toolbar-left {
    flex-wrap: wrap;
  }

  .app-code-editor__toolbar-right {
    justify-content: flex-end;
  }

  .app-code-editor__markdown-modes {
    width: auto;
  }
}

@media (prefers-reduced-motion: reduce) {
  .app-code-editor__expand-trigger span {
    transition: none;
  }
}
</style>
