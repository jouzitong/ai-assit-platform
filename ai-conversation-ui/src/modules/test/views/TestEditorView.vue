<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { AppCodeEditor } from '../../../components'
import type {
  AppCodeEditorFormat,
  AppCodeEditorFormatOption,
  AppCodeEditorMarkdownMode,
} from '../../../components'

type TestEditorFormat = Extract<AppCodeEditorFormat, 'markdown' | 'python' | 'json'>

const editorFormats: AppCodeEditorFormatOption[] = [
  { label: 'Markdown', value: 'markdown' },
  { label: 'Python', value: 'python' },
  { label: 'JSON', value: 'json' },
]

const formatLabels: Record<TestEditorFormat, string> = {
  markdown: 'Markdown',
  python: 'Python',
  json: 'JSON',
}

const initialContent: Record<TestEditorFormat, string> = {
  markdown: `# AppCodeEditor 交互测试

这是一个用于观察 **Markdown** 编辑体验的样例。

## 检查项

- 格式切换后内容是否正确保留
- 工具栏和语法状态是否清晰
- 键盘输入、选择和滚动是否顺畅

> 可以直接修改这段内容，状态区会同步更新。

\`\`\`json
{
  "component": "AppCodeEditor",
  "format": "markdown"
}
\`\`\`
`,
  python: `from dataclasses import dataclass


@dataclass
class EditorSample:
    name: str
    enabled: bool = True


def describe(sample: EditorSample) -> str:
    return f"{sample.name}: {'enabled' if sample.enabled else 'disabled'}"


print(describe(EditorSample(name="AppCodeEditor")))
`,
  json: `{
  "component": "AppCodeEditor",
  "formats": [
    "markdown",
    "python",
    "json"
  ],
  "settings": {
    "showToolbar": true,
    "showFormatSwitcher": true,
    "readonly": false
  }
}
`,
}

const activeFormat = ref<AppCodeEditorFormat>('markdown')
const contentByFormat = reactive<Record<TestEditorFormat, string>>({ ...initialContent })
const editorSettings = reactive({
  showToolbar: true,
  showFormatSwitcher: true,
  readonly: false,
  disabled: false,
  required: false,
  expandable: true,
  expandInitiallyFullscreen: false,
})
const lastEvent = ref('等待编辑器操作')
const markdownMode = ref<AppCodeEditorMarkdownMode>('edit')

const markdownModeLabels: Record<AppCodeEditorMarkdownMode, string> = {
  edit: '文本',
  split: '分屏',
  preview: '预览',
}

const resolvedFormat = computed<TestEditorFormat>(() => (
  activeFormat.value === 'python' || activeFormat.value === 'json'
    ? activeFormat.value
    : 'markdown'
))

const editorContent = computed({
  get: () => contentByFormat[resolvedFormat.value],
  set: value => {
    contentByFormat[resolvedFormat.value] = value
  },
})

const contentStats = computed(() => {
  const content = editorContent.value
  return {
    characters: content.length,
    lines: content.length === 0 ? 0 : content.split('\n').length,
  }
})

function handleChange(value: string) {
  lastEvent.value = `${formatLabels[resolvedFormat.value]} 内容已更新，共 ${value.length} 个字符`
}

function handleFormatChange(format: AppCodeEditorFormat) {
  lastEvent.value = `已切换到 ${format === 'python' || format === 'json' ? formatLabels[format] : formatLabels.markdown}`
}

function handleMarkdownModeChange(mode: AppCodeEditorMarkdownMode) {
  lastEvent.value = `Markdown 已切换到${markdownModeLabels[mode]}模式`
}

function resetCurrentContent() {
  contentByFormat[resolvedFormat.value] = initialContent[resolvedFormat.value]
  lastEvent.value = `${formatLabels[resolvedFormat.value]} 样例已重置`
}

function resetAll() {
  Object.assign(contentByFormat, initialContent)
  activeFormat.value = 'markdown'
  markdownMode.value = 'edit'
  Object.assign(editorSettings, {
    showToolbar: true,
    showFormatSwitcher: true,
    readonly: false,
    disabled: false,
    required: false,
    expandable: true,
    expandInitiallyFullscreen: false,
  })
  lastEvent.value = '测试状态已全部重置'
}
</script>

<template>
  <section class="test-editor-view">
    <header class="test-editor-view__header">
      <div>
        <p class="test-editor-view__eyebrow">COMPONENT PLAYGROUND</p>
        <h1>AppCodeEditor 交互测试</h1>
        <p class="test-editor-view__description">
          在同一页面验证 Markdown、Python 和 JSON 的编辑、语法反馈及不同组件状态。
        </p>
      </div>
      <el-button @click="resetAll">重置全部</el-button>
    </header>

    <div class="test-editor-view__workspace">
      <aside class="test-editor-view__controls">
        <section class="test-editor-view__control-section">
          <h2>测试格式</h2>
          <el-radio-group v-model="activeFormat" class="test-editor-view__format-list">
            <el-radio-button
              v-for="item in editorFormats"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </section>

        <section class="test-editor-view__control-section">
          <h2>组件状态</h2>
          <div class="test-editor-view__switches">
            <label>
              <span>显示工具栏</span>
              <el-switch v-model="editorSettings.showToolbar" />
            </label>
            <label>
              <span>显示格式选择器</span>
              <el-switch
                v-model="editorSettings.showFormatSwitcher"
                :disabled="!editorSettings.showToolbar"
              />
            </label>
            <label>
              <span>只读</span>
              <el-switch v-model="editorSettings.readonly" :disabled="editorSettings.disabled" />
            </label>
            <label>
              <span>禁用</span>
              <el-switch v-model="editorSettings.disabled" />
            </label>
            <label>
              <span>必填标识</span>
              <el-switch v-model="editorSettings.required" />
            </label>
            <label>
              <span>点击放大编辑</span>
              <el-switch v-model="editorSettings.expandable" />
            </label>
            <label>
              <span>弹窗默认全屏</span>
              <el-switch
                v-model="editorSettings.expandInitiallyFullscreen"
                :disabled="!editorSettings.expandable"
              />
            </label>
          </div>
        </section>

        <section class="test-editor-view__control-section test-editor-view__status">
          <div>
            <span>当前格式</span>
            <strong>{{ formatLabels[resolvedFormat] }}</strong>
          </div>
          <div>
            <span>内容统计</span>
            <strong>{{ contentStats.lines }} 行 / {{ contentStats.characters }} 字符</strong>
          </div>
          <div v-if="resolvedFormat === 'markdown'">
            <span>Markdown 模式</span>
            <strong>{{ markdownModeLabels[markdownMode] }}</strong>
          </div>
          <div>
            <span>最近事件</span>
            <strong>{{ lastEvent }}</strong>
          </div>
        </section>
      </aside>

      <main class="test-editor-view__editor-panel">
        <div class="test-editor-view__editor-heading">
          <div>
            <h2>{{ formatLabels[resolvedFormat] }} 样例</h2>
            <p>直接编辑内容或使用编辑器内的格式选择器切换样例。</p>
          </div>
          <el-button text type="primary" @click="resetCurrentContent">恢复当前样例</el-button>
        </div>

        <AppCodeEditor
          v-model="editorContent"
          v-model:format="activeFormat"
          v-model:markdown-mode="markdownMode"
          label="编辑器内容"
          hint="尝试输入错误语法、切换格式和改变组件状态，观察反馈是否符合预期。"
          placeholder="请输入内容"
          :formats="editorFormats"
          :show-toolbar="editorSettings.showToolbar"
          :show-format-switcher="editorSettings.showFormatSwitcher"
          :readonly="editorSettings.readonly"
          :disabled="editorSettings.disabled"
          :required="editorSettings.required"
          :expandable="editorSettings.expandable"
          :expand-initially-fullscreen="editorSettings.expandInitiallyFullscreen"
          expand-title="AppCodeEditor 大型编辑框"
          min-height="420px"
          :max-rows="26"
          @change="handleChange"
          @format-change="handleFormatChange"
          @markdown-mode-change="handleMarkdownModeChange"
        />
      </main>
    </div>
  </section>
</template>

<style scoped>
.test-editor-view {
  min-height: 100%;
  padding: var(--app-space-6);
  color: var(--app-text);
  background: var(--app-surface-muted);
  container: test-editor-view / inline-size;
}

.test-editor-view__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--app-space-5);
  margin-bottom: var(--app-space-6);
}

.test-editor-view__eyebrow {
  margin: 0 0 var(--app-space-2);
  color: var(--app-accent);
  font-size: var(--app-font-size-caption);
  font-weight: 700;
  letter-spacing: 0.08em;
}

.test-editor-view__header h1,
.test-editor-view__control-section h2,
.test-editor-view__editor-heading h2 {
  margin: 0;
  color: var(--app-title);
}

.test-editor-view__header h1 {
  font-size: var(--app-font-size-title-lg);
  line-height: var(--app-line-height-tight);
}

.test-editor-view__description,
.test-editor-view__editor-heading p {
  margin: var(--app-space-2) 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-body-lg);
  line-height: var(--app-line-height-body);
}

.test-editor-view__workspace {
  display: grid;
  grid-template-columns: minmax(240px, 300px) minmax(0, 1fr);
  gap: var(--app-space-5);
  align-items: start;
}

.test-editor-view__controls,
.test-editor-view__editor-panel {
  min-width: 0;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-xl);
  background: var(--app-surface-solid);
  box-shadow: var(--app-shadow-md);
}

.test-editor-view__controls {
  padding: var(--app-space-5);
}

.test-editor-view__control-section + .test-editor-view__control-section {
  padding-top: var(--app-space-5);
  margin-top: var(--app-space-5);
  border-top: 1px solid var(--app-border-subtle);
}

.test-editor-view__control-section h2,
.test-editor-view__editor-heading h2 {
  font-size: var(--app-font-size-title-sm);
}

.test-editor-view__format-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  width: 100%;
  margin-top: var(--app-space-3);
}

.test-editor-view__format-list :deep(.el-radio-button),
.test-editor-view__format-list :deep(.el-radio-button__inner) {
  width: 100%;
}

.test-editor-view__switches {
  display: grid;
  gap: var(--app-space-3);
  margin-top: var(--app-space-3);
}

.test-editor-view__switches label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-4);
  min-height: var(--app-control-height-sm);
  font-size: var(--app-font-size-body-lg);
}

.test-editor-view__status {
  display: grid;
  gap: var(--app-space-3);
}

.test-editor-view__status div {
  display: grid;
  gap: var(--app-space-1);
}

.test-editor-view__status span {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.test-editor-view__status strong {
  color: var(--app-text);
  font-size: var(--app-font-size-body);
  font-weight: 600;
  line-height: var(--app-line-height-body);
  overflow-wrap: anywhere;
}

.test-editor-view__editor-panel {
  padding: var(--app-space-6);
}

.test-editor-view__editor-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--app-space-4);
  margin-bottom: var(--app-space-5);
}

@container test-editor-view (max-width: 760px) {
  .test-editor-view__workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .test-editor-view__format-list {
    max-width: 360px;
  }
}

@container test-editor-view (max-width: 520px) {
  .test-editor-view__header,
  .test-editor-view__editor-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .test-editor-view__editor-panel,
  .test-editor-view__controls {
    padding: var(--app-space-4);
  }
}
</style>
