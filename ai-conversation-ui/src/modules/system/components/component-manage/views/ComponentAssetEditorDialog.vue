<script setup lang="ts">
import {
  ArrowLeftBold,
  ArrowRightBold,
  Check,
  DataBoard,
  EditPen,
  Loading,
  RefreshLeft,
  Search,
  Setting,
  Tickets,
} from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { computed, nextTick, reactive, ref, watch } from 'vue'
import {
  APPLICATION_COMPONENT_MANIFEST,
  findApplicationComponent,
  type ApplicationComponentDefinition,
} from '../../../../../application/component-manifest'
import {
  AppCodeEditor,
  AppDialog,
  LayoutDialogFooter,
  LayoutFormGrid,
  LayoutFormGridItem,
  useAppConfirm,
} from '../../../../../components'
import {
  bindApplicationComponent,
  buildComponentAssetDocument,
  buildComponentAssetEnvelope,
  buildComponentAssetRenderExampleJson,
  createComponentAssetDraft,
  parseComponentAssetRenderExample,
  RENDER_COMPONENT_KNOWLEDGE_BASE_SETTING_KEY as KNOWLEDGE_BASE_SETTING_KEY,
  toComponentAssetSubmission,
  type ComponentAssetDraft,
  type ComponentAssetRecord,
  type ComponentAssetSubmission,
} from '../service/componentAsset'

const props = withDefaults(defineProps<{
  modelValue: boolean
  mode?: 'create' | 'edit'
  initialValue?: ComponentAssetRecord | null
  categoryOptions?: Array<{ label: string, value: string }>
  existingSourceKeys?: string[]
  knowledgeBaseId?: string
  knowledgeBaseLoading?: boolean
  knowledgeBaseError?: string
  submitting?: boolean
}>(), {
  mode: 'create',
  initialValue: null,
  categoryOptions: () => [],
  existingSourceKeys: () => [],
  knowledgeBaseId: '',
  knowledgeBaseLoading: false,
  knowledgeBaseError: '',
  submitting: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [value: ComponentAssetSubmission]
  'edit-existing': [componentKey: string]
  closed: []
}>()

const appConfirm = useAppConfirm()
const formRef = ref<FormInstance>()
const currentStep = ref<0 | 1>(0)
const componentKeyword = ref('')
const activeCatalogCategory = ref('all')
const previewTab = ref<'markdown' | 'json'>('markdown')
const markdownContent = ref('')
const renderJsonContent = ref('')
const defaultMarkdownContent = ref('')
const defaultRenderJsonContent = ref('')
const initialSignature = ref('')
const resetting = ref(false)
const discardConfirming = ref(false)
const draft = reactive<ComponentAssetDraft>(createComponentAssetDraft())

const formRules: FormRules = {
  name: [
    { required: true, message: '请输入资产名称', trigger: 'blur' },
    { min: 2, max: 80, message: '资产名称长度应为 2 到 80 个字符', trigger: 'blur' },
  ],
}

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value) => {
    if (value || !props.submitting) {
      emit('update:modelValue', value)
    }
  },
})

const dialogTitle = computed(() => props.mode === 'create' ? '新建组件知识资产' : '编辑组件知识资产')
const selectedDefinition = computed(() => findApplicationComponent(draft.sourceKey))
const existingSourceKeySet = computed(() => new Set(
  props.existingSourceKeys.map(key => key.trim().toLowerCase()).filter(Boolean),
))

const publicComponents = computed(() => (
  APPLICATION_COMPONENT_MANIFEST.filter(definition => definition.exposure === 'public')
))

const catalogCategories = computed(() => [
  { key: 'all', label: '全部组件', count: publicComponents.value.length },
  ...Array.from(new Set(publicComponents.value.map(item => item.category))).map(category => ({
    key: category,
    label: category,
    count: publicComponents.value.filter(item => item.category === category).length,
  })),
])

const filteredComponents = computed(() => {
  const keyword = componentKeyword.value.trim().toLowerCase()
  return publicComponents.value.filter((item) => {
    const categoryMatched = activeCatalogCategory.value === 'all' || item.category === activeCatalogCategory.value
    const keywordMatched = !keyword || [item.name, item.key, item.description, item.sourcePath, ...item.tags]
      .some(value => value.toLowerCase().includes(keyword))
    return categoryMatched && keywordMatched
  })
})

const assetCategoryOptions = computed(() => {
  const options = [
    ...props.categoryOptions,
    ...publicComponents.value.map(item => ({ label: item.category, value: item.category })),
  ]
  return options.filter((item, index) => options.findIndex(target => target.value === item.value) === index)
})

const renderJsonError = computed(() => validateRenderJson(renderJsonContent.value))
const markdownError = computed(() => validateMarkdown(markdownContent.value))
const knowledgeBaseReady = computed(() => (
  !props.knowledgeBaseLoading
  && !props.knowledgeBaseError
  && Boolean(props.knowledgeBaseId.trim())
))
const saveDisabled = computed(() => (
  props.submitting
  || !selectedDefinition.value
  || !draft.name.trim()
  || !knowledgeBaseReady.value
  || Boolean(markdownError.value)
  || Boolean(renderJsonError.value)
))

function componentIcon(definition: ApplicationComponentDefinition) {
  if (definition.category === '数据可视化') return DataBoard
  if (definition.category === '表单交互') return EditPen
  return Tickets
}

function isExistingComponent(componentKey: string) {
  return props.mode === 'create' && existingSourceKeySet.value.has(componentKey.toLowerCase())
}

function syncKnowledgeBase() {
  draft.knowledgeBaseId = props.knowledgeBaseId.trim()
  draft.knowledgeBaseSettingKey = KNOWLEDGE_BASE_SETTING_KEY
  // 兼容 service 迁移期间的旧字段，最终 envelope 由 service 统一组装。
  draft.knowledgeBaseCode = props.knowledgeBaseId.trim()
}

function buildDefaultContents() {
  syncKnowledgeBase()
  return {
    markdown: buildComponentAssetDocument(draft),
    renderJson: buildComponentAssetRenderExampleJson(draft),
  }
}

function replaceWithGeneratedDefaults() {
  const generated = buildDefaultContents()
  defaultMarkdownContent.value = generated.markdown
  defaultRenderJsonContent.value = generated.renderJson
  markdownContent.value = generated.markdown
  renderJsonContent.value = generated.renderJson
}

function refreshGeneratedDefaults() {
  if (!selectedDefinition.value) return
  const markdownWasDefault = !markdownContent.value.trim() || markdownContent.value === defaultMarkdownContent.value
  const renderJsonWasDefault = !renderJsonContent.value.trim() || renderJsonContent.value === defaultRenderJsonContent.value
  const generated = buildDefaultContents()

  defaultMarkdownContent.value = generated.markdown
  defaultRenderJsonContent.value = generated.renderJson
  if (markdownWasDefault) markdownContent.value = generated.markdown
  if (renderJsonWasDefault) renderJsonContent.value = generated.renderJson
}

function readPersistedRenderExample(value?: string) {
  if (!value?.trim()) return ''
  try {
    const parsed = JSON.parse(value) as unknown
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return ''
    const record = parsed as Record<string, unknown>
    if (record.protocol === 'render-json') {
      return JSON.stringify(record, null, 2)
    }
    if (record.renderExample && typeof record.renderExample === 'object') {
      return JSON.stringify(record.renderExample, null, 2)
    }
  }
  catch {
    return ''
  }
  return ''
}

function editorSignature() {
  return JSON.stringify({
    sourceKey: draft.sourceKey,
    name: draft.name,
    category: draft.category,
    status: draft.status,
    markdown: markdownContent.value,
    renderJson: renderJsonContent.value,
  })
}

const isDirty = computed(() => Boolean(props.modelValue) && editorSignature() !== initialSignature.value)

async function resetEditor() {
  resetting.value = true
  Object.assign(draft, createComponentAssetDraft(props.initialValue))
  syncKnowledgeBase()
  currentStep.value = props.mode === 'edit' && draft.sourceKey ? 1 : 0
  componentKeyword.value = ''
  activeCatalogCategory.value = 'all'
  previewTab.value = 'markdown'

  if (selectedDefinition.value) {
    const generated = buildDefaultContents()
    defaultMarkdownContent.value = generated.markdown
    defaultRenderJsonContent.value = generated.renderJson
    markdownContent.value = props.initialValue?.docMarkdown?.trim() || generated.markdown
    renderJsonContent.value = readPersistedRenderExample(props.initialValue?.exampleJson) || generated.renderJson
  } else {
    defaultMarkdownContent.value = ''
    defaultRenderJsonContent.value = ''
    markdownContent.value = ''
    renderJsonContent.value = ''
  }

  await nextTick()
  formRef.value?.clearValidate()
  initialSignature.value = editorSignature()
  resetting.value = false
}

async function selectComponent(componentKey: string) {
  if (isExistingComponent(componentKey)) {
    if (!await confirmDiscardChanges()) return
    emit('edit-existing', componentKey)
    return
  }
  if (draft.sourceKey === componentKey || props.submitting) return
  if (draft.sourceKey) {
    const confirmed = await appConfirm('更换源组件会重置已生成的 Markdown 与 Render JSON，是否继续？', {
      title: '更换源组件',
      confirmButtonText: '确认更换',
      danger: true,
    })
    if (!confirmed) return
  }

  bindApplicationComponent(draft, componentKey)
  syncKnowledgeBase()
  replaceWithGeneratedDefaults()
  await nextTick()
  formRef.value?.clearValidate()
}

function goNext() {
  if (!selectedDefinition.value) {
    ElMessage.warning('请先选择一个公开 Renderer')
    return
  }
  refreshGeneratedDefaults()
  currentStep.value = 1
}

function goBack() {
  if (props.mode === 'create' && !props.submitting) {
    currentStep.value = 0
  }
}

function validateRenderJson(value: string) {
  try {
    const renderExample = parseComponentAssetRenderExample(value)
    if (selectedDefinition.value) {
      buildComponentAssetEnvelope(draft, renderExample)
    }
  }
  catch (error) {
    return error instanceof Error ? error.message : 'Render JSON 格式不正确'
  }
  return ''
}

function validateMarkdown(value: string) {
  if (!value.trim()) return 'Markdown 资产文档不能为空'
  const requiredSections = ['## 4. 参数契约', '## 6. 事件契约']
  const missingSections = requiredSections.filter(section => !value.includes(section))
  if (missingSections.length) {
    return `Markdown 必须保留固定章节：${missingSections.join('、')}`
  }

  const parameterSectionStart = value.indexOf('## 4. 参数契约')
  const parameterSectionEnd = value.indexOf('## 5.', parameterSectionStart)
  const parameterSection = value.slice(
    parameterSectionStart + '## 4. 参数契约'.length,
    parameterSectionEnd < 0 ? value.length : parameterSectionEnd,
  )
  const tableRows = parameterSection
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(line => line.startsWith('|') && line.endsWith('|'))
    .map(splitMarkdownTableRow)
  const header = tableRows[0] || []
  const separator = tableRows[1] || []
  const parameterRows = tableRows.slice(2)
  const missingParameterKeys = (selectedDefinition.value?.parameters || [])
    .map(parameter => parameter.key)
    .filter(key => !parameterRows.some(row => row[0] === key))
  if (
    header.length !== 6
    || header[0] !== '参数'
    || header[1] !== '类型'
    || header[2] !== '必填'
    || separator.length !== 6
    || !separator.every(column => /^:?-{3,}:?$/.test(column))
    || !parameterRows.length
    || parameterRows.some(row => row.length !== 6 || !row[0] || !row[1])
  ) {
    return 'Markdown 参数契约表必须保留 6 列表头、分隔行和完整参数行'
  }
  if (missingParameterKeys.length) {
    return `Markdown 参数契约表缺少参数：${missingParameterKeys.join('、')}`
  }
  return ''
}

function splitMarkdownTableRow(line: string) {
  const columns: string[] = []
  let buffer = ''
  let escaped = false
  for (const character of line.slice(1, -1)) {
    if (escaped) {
      buffer += character
      escaped = false
    }
    else if (character === '\\') {
      escaped = true
    }
    else if (character === '|') {
      columns.push(buffer.trim())
      buffer = ''
    }
    else {
      buffer += character
    }
  }
  if (escaped) buffer += '\\'
  columns.push(buffer.trim())
  return columns
}

async function restoreDefaults() {
  if (props.submitting) return
  const generated = buildDefaultContents()
  const changed = markdownContent.value !== generated.markdown || renderJsonContent.value !== generated.renderJson
  if (changed) {
    const confirmed = await appConfirm('恢复后将覆盖当前编辑的 Markdown 与 Render JSON，是否继续？', {
      title: '恢复自动生成内容',
      confirmButtonText: '恢复默认',
      danger: true,
    })
    if (!confirmed) return
  }
  defaultMarkdownContent.value = generated.markdown
  defaultRenderJsonContent.value = generated.renderJson
  markdownContent.value = generated.markdown
  renderJsonContent.value = generated.renderJson
  ElMessage.success('已恢复自动生成内容')
}

async function submitAsset() {
  if (props.submitting) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) return
  if (props.knowledgeBaseLoading) {
    ElMessage.warning('正在读取组件知识库 ID，请稍候')
    return
  }
  if (props.knowledgeBaseError) {
    ElMessage.error(props.knowledgeBaseError)
    return
  }
  if (!props.knowledgeBaseId.trim()) {
    ElMessage.warning(`请先配置系统参数 ${KNOWLEDGE_BASE_SETTING_KEY}`)
    return
  }
  syncKnowledgeBase()
  refreshGeneratedDefaults()
  if (markdownError.value) {
    previewTab.value = 'markdown'
    ElMessage.warning(markdownError.value)
    return
  }
  if (renderJsonError.value) {
    previewTab.value = 'json'
    ElMessage.warning(renderJsonError.value)
    return
  }

  try {
    emit('submit', toComponentAssetSubmission(draft, {
      docMarkdown: markdownContent.value,
      renderExampleJson: renderJsonContent.value,
    }))
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识资产组装失败')
  }
}

async function confirmDiscardChanges() {
  if (!isDirty.value) return true
  if (discardConfirming.value) return false
  discardConfirming.value = true
  try {
    return await appConfirm('当前修改尚未保存，关闭后将无法恢复，是否继续？', {
      title: '放弃未保存修改',
      confirmButtonText: '放弃修改',
      danger: true,
    })
  }
  finally {
    discardConfirming.value = false
  }
}

async function requestClose() {
  if (props.submitting) return
  if (await confirmDiscardChanges()) {
    dialogVisible.value = false
  }
}

async function handleBeforeClose(done: () => void) {
  if (props.submitting) return
  if (await confirmDiscardChanges()) {
    done()
  }
}

watch(() => props.modelValue, (visible) => {
  if (visible) void resetEditor()
}, { immediate: true })

watch(() => [draft.name, draft.category, draft.status], () => {
  if (props.modelValue && !resetting.value) refreshGeneratedDefaults()
})

watch(() => props.knowledgeBaseId, async () => {
  if (!props.modelValue) return
  const wasDirty = !resetting.value && isDirty.value
  syncKnowledgeBase()
  refreshGeneratedDefaults()
  if (!wasDirty) {
    await nextTick()
    initialSignature.value = editorSignature()
  }
})
</script>

<template>
  <AppDialog
    v-model="dialogVisible"
    class="component-asset-editor-dialog"
    :title="dialogTitle"
    description="选择公开 Renderer，系统将自动生成 Markdown 知识文档与可运行的 Render JSON。"
    size="extra-large"
    height="86%"
    action-mode="none"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
    :before-close="handleBeforeClose"
    @closed="emit('closed')"
  >
    <div class="component-asset-editor">
      <div class="component-asset-editor__steps" aria-label="组件知识资产创建步骤">
        <el-steps :active="currentStep" simple finish-status="success">
          <el-step title="选择公开 Renderer" />
          <el-step title="生成知识资产" />
        </el-steps>
      </div>

      <section v-if="currentStep === 0" class="component-asset-step" aria-labelledby="component-source-title">
        <div class="component-asset-step__heading">
          <div>
            <span class="component-asset-step__index">01</span>
            <h3 id="component-source-title">选择公开 Renderer</h3>
            <p>所有公开 Application Renderer 都会进入候选目录，并携带参数与示例契约。</p>
          </div>
          <el-input v-model="componentKeyword" clearable placeholder="搜索名称、Key、标签或源码路径">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>

        <div class="component-catalog-filter" role="group" aria-label="Renderer 分类">
          <button
            v-for="category in catalogCategories"
            :key="category.key"
            type="button"
            :class="['component-catalog-filter__item', { 'is-active': activeCatalogCategory === category.key }]"
            :aria-pressed="activeCatalogCategory === category.key"
            @click="activeCatalogCategory = category.key"
          >
            {{ category.label }} <span>{{ category.count }}</span>
          </button>
        </div>

        <div v-if="filteredComponents.length" class="component-catalog-grid">
          <button
            v-for="component in filteredComponents"
            :key="component.key"
            type="button"
            :class="[
              'component-catalog-card',
              {
                'is-selected': draft.sourceKey === component.key,
                'is-existing': isExistingComponent(component.key),
              },
            ]"
            :aria-pressed="draft.sourceKey === component.key"
            :aria-label="isExistingComponent(component.key) ? `${component.name} 已创建，点击编辑` : component.name"
            :disabled="submitting"
            @click="selectComponent(component.key)"
          >
            <span class="component-catalog-card__icon" aria-hidden="true">
              <el-icon><component :is="componentIcon(component)" /></el-icon>
            </span>
            <span class="component-catalog-card__content">
              <span class="component-catalog-card__topline">
                <strong>{{ component.name }}</strong>
                <el-tag v-if="isExistingComponent(component.key)" size="small" effect="plain" type="info">
                  已创建 · 编辑
                </el-tag>
                <el-icon v-if="draft.sourceKey === component.key" class="component-catalog-card__check"><Check /></el-icon>
              </span>
              <code>{{ component.key }}</code>
              <span class="component-catalog-card__description">{{ component.description }}</span>
              <span class="component-catalog-card__meta">
                <span>{{ component.category }}</span>
                <span>{{ component.parameters.length }} 个参数</span>
                <span>{{ component.events.length }} 个事件</span>
              </span>
              <small>{{ component.sourcePath }}</small>
            </span>
          </button>
        </div>
        <el-empty v-else description="没有匹配的公开 Renderer" :image-size="72" />
      </section>

      <section v-else class="component-asset-step" aria-labelledby="component-document-title">
        <div class="component-asset-step__heading component-asset-step__heading--compact">
          <div>
            <span class="component-asset-step__index">02</span>
            <h3 id="component-document-title">查看并保存知识资产</h3>
            <p>基础信息变化会同步更新尚未手动修改的默认内容；恢复默认可重新完整生成。</p>
          </div>
          <el-button :disabled="submitting" @click="restoreDefaults">
            <el-icon><RefreshLeft /></el-icon>
            恢复默认
          </el-button>
        </div>

        <div v-if="selectedDefinition" class="component-selected-renderer">
          <span class="component-selected-renderer__icon" aria-hidden="true">
            <el-icon><component :is="componentIcon(selectedDefinition)" /></el-icon>
          </span>
          <div>
            <strong>{{ selectedDefinition.name }}</strong>
            <code>{{ selectedDefinition.key }}</code>
          </div>
          <el-tag effect="plain">{{ selectedDefinition.category }}</el-tag>
        </div>

        <div class="component-asset-panel">
          <div class="component-asset-panel__title">
            <span><el-icon><Setting /></el-icon></span>
            <div><h4>资产信息</h4><p>知识库 ID 由系统参数统一提供，不在此处手工修改。</p></div>
          </div>

          <el-form ref="formRef" :model="draft" :rules="formRules" label-position="top" status-icon>
            <LayoutFormGrid :columns="2">
              <LayoutFormGridItem>
                <el-form-item label="资产名称" prop="name" required>
                  <el-input v-model="draft.name" :disabled="submitting" placeholder="请输入资产名称" maxlength="80" show-word-limit />
                </el-form-item>
              </LayoutFormGridItem>
              <LayoutFormGridItem>
                <el-form-item label="业务分类">
                  <el-select
                    v-model="draft.category"
                    :disabled="submitting"
                    allow-create
                    filterable
                    default-first-option
                    placeholder="输入或选择分类"
                  >
                    <el-option v-for="option in assetCategoryOptions" :key="option.value" :label="option.label" :value="option.value" />
                  </el-select>
                </el-form-item>
              </LayoutFormGridItem>
              <LayoutFormGridItem>
                <el-form-item label="资产状态">
                  <el-select v-model="draft.status" :disabled="submitting">
                    <el-option label="草稿" :value="1" />
                    <el-option label="已发布" :value="2" />
                    <el-option label="已停用" :value="3" />
                  </el-select>
                </el-form-item>
              </LayoutFormGridItem>
              <LayoutFormGridItem>
                <el-form-item label="知识库 ID" required>
                  <el-input :model-value="knowledgeBaseId" readonly :placeholder="knowledgeBaseLoading ? '正在读取系统参数...' : '尚未配置'">
                    <template v-if="knowledgeBaseLoading" #suffix>
                      <el-icon class="component-asset-editor__loading"><Loading /></el-icon>
                    </template>
                  </el-input>
                  <div class="component-knowledge-base-hint">
                    来源：系统参数 <code>{{ KNOWLEDGE_BASE_SETTING_KEY }}</code>
                  </div>
                </el-form-item>
              </LayoutFormGridItem>
            </LayoutFormGrid>
          </el-form>

          <el-alert
            v-if="knowledgeBaseError"
            :title="knowledgeBaseError"
            type="error"
            show-icon
            :closable="false"
          />
          <el-alert
            v-else-if="!knowledgeBaseLoading && !knowledgeBaseId"
            :title="`未读取到 ${KNOWLEDGE_BASE_SETTING_KEY}，配置后才能保存知识资产。`"
            type="warning"
            show-icon
            :closable="false"
          />
        </div>

        <div class="component-document-workspace">
          <div class="component-document-workspace__toolbar">
            <el-tabs v-model="previewTab">
              <el-tab-pane label="Markdown 知识文档" name="markdown" />
              <el-tab-pane label="Render JSON" name="json" />
            </el-tabs>
            <span>内容会在保存时重新封装为组件资产</span>
          </div>

          <AppCodeEditor
            v-show="previewTab === 'markdown'"
            v-model="markdownContent"
            format="markdown"
            label="Markdown 知识文档"
            :error="markdownError"
            :disabled="submitting"
            :show-format-switcher="false"
            min-height="380px"
            :max-rows="22"
            expandable
            expand-title="编辑 Markdown 知识文档"
          />
          <AppCodeEditor
            v-show="previewTab === 'json'"
            v-model="renderJsonContent"
            format="json"
            label="Render JSON"
            hint="必须是 protocol=render-json 且包含 root.component 的可运行文档。"
            :error="renderJsonError"
            :disabled="submitting"
            :show-format-switcher="false"
            min-height="380px"
            :max-rows="22"
            expandable
            expand-title="编辑 Render JSON"
          />
        </div>
      </section>
    </div>

    <template #footer>
      <LayoutDialogFooter align="between" class="component-asset-editor__footer">
        <el-button :disabled="submitting || discardConfirming" @click="requestClose">取消</el-button>
        <div class="component-asset-editor__footer-actions">
          <el-button v-if="currentStep === 1 && mode === 'create'" :disabled="submitting" @click="goBack">
            <el-icon><ArrowLeftBold /></el-icon>
            上一步
          </el-button>
          <el-button v-if="currentStep === 0" type="primary" :disabled="!selectedDefinition || submitting" @click="goNext">
            生成知识资产
            <el-icon><ArrowRightBold /></el-icon>
          </el-button>
          <el-button v-else type="primary" :loading="submitting" :disabled="saveDisabled" @click="submitAsset">
            <el-icon><Check /></el-icon>
            {{ mode === 'create' ? '保存知识资产' : '保存资产变更' }}
          </el-button>
        </div>
      </LayoutDialogFooter>
    </template>
  </AppDialog>
</template>

<style src="../styles/component-asset-editor.scss"></style>
