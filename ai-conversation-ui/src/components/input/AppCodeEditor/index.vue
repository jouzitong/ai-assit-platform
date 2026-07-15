<script setup lang="ts">
import { basicSetup } from 'codemirror'
import { json } from '@codemirror/lang-json'
import { javascript } from '@codemirror/lang-javascript'
import { python } from '@codemirror/lang-python'
import { markdown } from '@codemirror/lang-markdown'
import { syntaxTree, type Extension } from '@codemirror/language'
import { EditorState, Compartment } from '@codemirror/state'
import { EditorView, keymap, placeholder as cmPlaceholder } from '@codemirror/view'
import { lintGutter, linter, type Diagnostic } from '@codemirror/lint'
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useResponsiveOverlayTarget } from '../../../composables/useResponsiveViewport'
import AppFieldShell from '../shared/AppFieldShell.vue'

defineOptions({
  inheritAttrs: false,
})

type EditorFormat = 'json' | 'python' | 'javascript' | 'markdown' | 'asciidoc' | 'text'

type FormatOption = {
  label: string
  value: EditorFormat
}

const DEFAULT_FORMAT_OPTIONS: FormatOption[] = [
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
    format?: EditorFormat
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
    toolbarLabel?: string
    formats?: FormatOption[]
    labelPosition?: 'left' | 'inner'
  }>(),
  {
    modelValue: '',
    format: 'text',
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
    labelPosition: 'inner',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:format': [value: EditorFormat]
  change: [value: string]
  'format-change': [value: EditorFormat]
}>()

const editorRef = ref<HTMLElement | null>(null)
const editorView = ref<EditorView | null>(null)
const diagnostics = ref<Diagnostic[]>([])
const checking = ref(true)
const responsiveOverlayTarget = useResponsiveOverlayTarget()
let diagnosticsTimer: number | null = null

const editableCompartment = new Compartment()
const languageCompartment = new Compartment()
const placeholderCompartment = new Compartment()

const selectedFormat = computed({
  get: () => props.format,
  set: (value: EditorFormat) => {
    emit('update:format', value)
    emit('format-change', value)
  },
})

const toolbarFormats = computed(() => (props.formats?.length ? props.formats : DEFAULT_FORMAT_OPTIONS)
  .filter(option => DEFAULT_FORMAT_OPTIONS.some(item => item.value === option.value)))

const diagnosticsSummary = computed(() => {
  if (checking.value) {
    return '检查中...'
  }
  if (diagnostics.value.length === 0) {
    return '语法正常'
  }
  return `发现 ${diagnostics.value.length} 个语法问题`
})

const diagnosticsType = computed(() => {
  if (checking.value) {
    return 'info'
  }
  return diagnostics.value.length > 0 ? 'danger' : 'success'
})
const editorMaxHeight = computed(() => {
  if (!props.maxRows || props.maxRows <= 0) {
    return ''
  }
  return `calc(${props.maxRows} * 1.65em + 28px)`
})

function buildLanguageExtension(format: EditorFormat): Extension {
  switch (format) {
    case 'json':
      return json()
    case 'javascript':
      return javascript()
    case 'python':
      return python()
    case 'markdown':
      return markdown()
    case 'asciidoc':
    case 'text':
    default:
      return []
  }
}

function buildEditableExtension() {
  return EditorState.readOnly.of(props.disabled || props.readonly)
}

function computeSyntaxDiagnostics(state: EditorState): Diagnostic[] {
  if (props.format === 'asciidoc' || props.format === 'text') {
    return []
  }

  const tree = syntaxTree(state)
  const cursor = tree.cursor()
  const nextDiagnostics: Diagnostic[] = []
  do {
    if (!cursor.type.isError) {
      continue
    }
    nextDiagnostics.push({
      from: cursor.from,
      to: Math.max(cursor.to, cursor.from + 1),
      severity: 'error',
      message: '存在语法错误，请检查附近内容。',
    })
  } while (cursor.next())

  return nextDiagnostics
}

function clearDiagnosticsTimer() {
  if (diagnosticsTimer !== null) {
    window.clearTimeout(diagnosticsTimer)
    diagnosticsTimer = null
  }
}

function scheduleDiagnostics(delay = 120) {
  const view = editorView.value
  if (!view) {
    return
  }
  clearDiagnosticsTimer()
  checking.value = true
  diagnosticsTimer = window.setTimeout(() => {
    diagnostics.value = computeSyntaxDiagnostics(view.state)
    checking.value = false
    diagnosticsTimer = null
  }, delay)
}

function createEditor() {
  if (!editorRef.value) {
    return
  }

  const state = EditorState.create({
    doc: props.modelValue,
    extensions: [
      basicSetup,
      history(),
      keymap.of([...defaultKeymap, ...historyKeymap]),
      EditorView.lineWrapping,
      lintGutter(),
      linter(view => computeSyntaxDiagnostics(view.state)),
      languageCompartment.of(buildLanguageExtension(props.format)),
      editableCompartment.of(buildEditableExtension()),
      placeholderCompartment.of(props.placeholder ? cmPlaceholder(props.placeholder) : []),
      EditorView.updateListener.of((update) => {
        if (!update.docChanged) {
          return
        }
        const value = update.state.doc.toString()
        scheduleDiagnostics()
        emit('update:modelValue', value)
        emit('change', value)
      }),
      EditorView.theme({
        '&': {
          height: props.height || '100%',
          minHeight: props.minHeight,
          fontSize: '13px',
          borderRadius: '14px',
          overflow: 'hidden',
          backgroundColor: 'var(--app-code-editor-surface)',
          color: 'var(--app-code-editor-text)',
        },
        '.cm-scroller': {
          fontFamily: "Menlo, Monaco, Consolas, 'Courier New', monospace",
          lineHeight: '1.65',
        },
        '.cm-content': {
          padding: 'var(--app-space-comfortable) 0',
        },
        '.cm-line': {
          padding: '0 var(--app-space-4)',
        },
        '.cm-gutters': {
          backgroundColor: 'var(--app-code-editor-gutter-bg)',
          color: 'var(--app-code-editor-gutter-text)',
          borderRight: '1px solid var(--app-code-editor-border-soft)',
        },
        '.cm-activeLine': {
          backgroundColor: 'var(--app-code-editor-active-line)',
        },
        '.cm-activeLineGutter': {
          backgroundColor: 'var(--app-code-editor-active-gutter)',
        },
        '.cm-focused': {
          outline: 'none',
        },
        '.cm-editor.cm-focused': {
          boxShadow: 'inset 0 0 0 1px var(--app-code-editor-focus)',
        },
        '.cm-content, .cm-line, .cm-placeholder': {
          color: 'var(--app-code-editor-text)',
        },
        '.cm-placeholder': {
          color: 'var(--app-code-editor-placeholder)',
        },
        '.cm-tooltip-lint': {
          fontFamily: 'inherit',
        },
      }),
    ],
  })

  editorView.value = new EditorView({
    state,
    parent: editorRef.value,
  })
  scheduleDiagnostics(160)
}

function destroyEditor() {
  clearDiagnosticsTimer()
  editorView.value?.destroy()
  editorView.value = null
}

onMounted(() => {
  createEditor()
})

onBeforeUnmount(() => {
  destroyEditor()
})

watch(
  () => props.modelValue,
  (value) => {
    const view = editorView.value
    if (!view) {
      return
    }
    const current = view.state.doc.toString()
    if (value === current) {
      return
    }
    view.dispatch({
      changes: {
        from: 0,
        to: current.length,
        insert: value || '',
      },
    })
    scheduleDiagnostics()
  },
)

watch(
  () => props.format,
  (value) => {
    const view = editorView.value
    if (!view) {
      return
    }
    view.dispatch({
      effects: languageCompartment.reconfigure(buildLanguageExtension(value)),
    })
    scheduleDiagnostics(160)
  },
)

watch(
  () => [props.disabled, props.readonly],
  () => {
    const view = editorView.value
    if (!view) {
      return
    }
    view.dispatch({
      effects: editableCompartment.reconfigure(buildEditableExtension()),
    })
  },
)

watch(
  () => props.placeholder,
  (value) => {
    const view = editorView.value
    if (!view) {
      return
    }
    view.dispatch({
      effects: placeholderCompartment.reconfigure(value ? cmPlaceholder(value) : []),
    })
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
        </div>
        <div class="app-code-editor__toolbar-right">
          <el-tag size="small" effect="plain" :type="diagnosticsType">
            {{ diagnosticsSummary }}
          </el-tag>
        </div>
      </div>
      <div ref="editorRef" class="app-code-editor__surface" />
    </div>
  </AppFieldShell>
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

.app-code-editor__surface {
  min-height: var(--app-code-editor-min-height);
  height: var(--app-code-editor-height);
  max-height: var(--app-code-editor-max-height);
}

.app-code-editor__surface :deep(.cm-editor) {
  height: 100%;
  max-height: var(--app-code-editor-max-height);
}

.app-code-editor__surface :deep(.cm-scroller) {
  overflow: auto;
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
}
</style>
