<script setup lang="ts">
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands'
import { json } from '@codemirror/lang-json'
import { javascript } from '@codemirror/lang-javascript'
import { markdown } from '@codemirror/lang-markdown'
import { python } from '@codemirror/lang-python'
import { syntaxTree, type Extension } from '@codemirror/language'
import { lintGutter, linter, type Diagnostic } from '@codemirror/lint'
import { Compartment, EditorState } from '@codemirror/state'
import { EditorView, keymap, placeholder as cmPlaceholder } from '@codemirror/view'
import { basicSetup } from 'codemirror'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { AppCodeEditorFormat, AppCodeEditorStatus } from './types'

const props = withDefaults(defineProps<{
  modelValue?: string
  format?: AppCodeEditorFormat
  placeholder?: string
  disabled?: boolean
  readonly?: boolean
  height?: string
  minHeight?: string
  maxHeight?: string
}>(), {
  modelValue: '',
  format: 'text',
  placeholder: '',
  disabled: false,
  readonly: false,
  height: '100%',
  minHeight: '320px',
  maxHeight: 'none',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string]
  'status-change': [status: AppCodeEditorStatus]
}>()

const editorRef = ref<HTMLElement | null>(null)
const editorView = ref<EditorView | null>(null)
let diagnosticsTimer: number | null = null

const editableCompartment = new Compartment()
const languageCompartment = new Compartment()
const placeholderCompartment = new Compartment()

function buildLanguageExtension(format: AppCodeEditorFormat): Extension {
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
  const diagnostics: Diagnostic[] = []
  do {
    if (!cursor.type.isError) {
      continue
    }
    diagnostics.push({
      from: cursor.from,
      to: Math.max(cursor.to, cursor.from + 1),
      severity: 'error',
      message: '存在语法错误，请检查附近内容。',
    })
  } while (cursor.next())

  return diagnostics
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
  emit('status-change', { checking: true, diagnostics: 0 })
  diagnosticsTimer = window.setTimeout(() => {
    const diagnostics = computeSyntaxDiagnostics(view.state)
    emit('status-change', { checking: false, diagnostics: diagnostics.length })
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
          height: 'var(--app-code-editor-surface-height)',
          minHeight: 'var(--app-code-editor-surface-min-height)',
          maxHeight: 'var(--app-code-editor-surface-max-height)',
          overflow: 'hidden',
          backgroundColor: 'var(--app-code-editor-surface)',
          color: 'var(--app-code-editor-text)',
          fontSize: 'var(--app-font-size-body)',
        },
        '.cm-scroller': {
          fontFamily: "Menlo, Monaco, Consolas, 'Courier New', monospace",
          lineHeight: '1.65',
          overflow: 'auto',
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

function focus() {
  editorView.value?.focus()
}

onMounted(createEditor)
onBeforeUnmount(destroyEditor)

watch(
  () => props.modelValue,
  value => {
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
  value => {
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
    editorView.value?.dispatch({
      effects: editableCompartment.reconfigure(buildEditableExtension()),
    })
  },
)

watch(
  () => props.placeholder,
  value => {
    editorView.value?.dispatch({
      effects: placeholderCompartment.reconfigure(value ? cmPlaceholder(value) : []),
    })
  },
)

defineExpose({ focus })
</script>

<template>
  <div ref="editorRef" class="code-editor-surface" />
</template>

<style scoped>
.code-editor-surface {
  --app-code-editor-surface-height: v-bind('props.height');
  --app-code-editor-surface-min-height: v-bind('props.minHeight');
  --app-code-editor-surface-max-height: v-bind('props.maxHeight');
  min-width: 0;
  min-height: var(--app-code-editor-surface-min-height);
  height: var(--app-code-editor-surface-height);
  max-height: var(--app-code-editor-surface-max-height);
}
</style>
