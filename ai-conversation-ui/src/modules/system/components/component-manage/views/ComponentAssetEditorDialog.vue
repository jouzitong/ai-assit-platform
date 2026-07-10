<script setup lang="ts">
import {
  ArrowLeftBold,
  ArrowRightBold,
  Check,
  Connection,
  DataBoard,
  EditPen,
  Search,
  Setting,
  Tickets,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'
import {
  APPLICATION_COMPONENT_MANIFEST,
  findApplicationComponent,
  type ApplicationComponentDefinition,
} from '../../../../../application/component-manifest'
import { AppCodeEditor } from '../../../../../components'
import {
  bindApplicationComponent,
  buildComponentAssetDocument,
  buildComponentAssetExampleJson,
  createComponentAssetDraft,
  getKnowledgeDocumentCode,
  toComponentAssetSubmission,
  validateComponentAssetDraft,
  type ComponentAssetDraft,
  type ComponentAssetRecord,
  type ComponentAssetSubmission,
} from '../service/componentAsset'

const props = withDefaults(defineProps<{
  modelValue: boolean
  mode?: 'create' | 'edit'
  initialValue?: ComponentAssetRecord | null
  categoryOptions?: Array<{ label: string, value: string }>
  submitting?: boolean
}>(), {
  mode: 'create',
  initialValue: null,
  categoryOptions: () => [],
  submitting: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [value: ComponentAssetSubmission]
  closed: []
}>()

const currentStep = ref(0)
const componentKeyword = ref('')
const activeCatalogCategory = ref('all')
const previewTab = ref('markdown')
const draft = reactive<ComponentAssetDraft>(createComponentAssetDraft())

const dialogVisible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const dialogTitle = computed(() => props.mode === 'create' ? '新建组件数字资产' : '编辑组件数字资产')
const selectedDefinition = computed(() => findApplicationComponent(draft.sourceKey))
const generatedDocument = computed(() => buildComponentAssetDocument(draft))
const generatedExample = computed(() => buildComponentAssetExampleJson(draft))
const documentCode = computed(() => getKnowledgeDocumentCode(draft.key))
const enabledParameterCount = computed(() => Object.values(draft.parameters).filter(item => item.enabled).length)
const documentCharacterCount = computed(() => generatedDocument.value.length)

const catalogCategories = computed(() => [
  { key: 'all', label: '全部组件', count: APPLICATION_COMPONENT_MANIFEST.length },
  ...Array.from(new Set(APPLICATION_COMPONENT_MANIFEST.map(item => item.category))).map(category => ({
    key: category,
    label: category,
    count: APPLICATION_COMPONENT_MANIFEST.filter(item => item.category === category).length,
  })),
])

const filteredComponents = computed(() => {
  const keyword = componentKeyword.value.trim().toLowerCase()
  return APPLICATION_COMPONENT_MANIFEST.filter((item) => {
    const categoryMatched = activeCatalogCategory.value === 'all' || item.category === activeCatalogCategory.value
    const keywordMatched = !keyword || [item.name, item.key, item.description, item.sourcePath]
      .some(value => value.toLowerCase().includes(keyword))
    return categoryMatched && keywordMatched
  })
})

const assetCategoryOptions = computed(() => {
  const options = [
    ...props.categoryOptions,
    ...APPLICATION_COMPONENT_MANIFEST.map(item => ({ label: item.category, value: item.category })),
  ]
  return options.filter((item, index) => options.findIndex(target => target.value === item.value) === index)
})

const completionPercentage = computed(() => {
  if (!selectedDefinition.value) return 0
  if (!draft.key.trim() || !draft.name.trim() || !draft.summary.trim()) return 36
  if (currentStep.value < 2) return 68
  return 100
})

function componentIcon(definition: ApplicationComponentDefinition) {
  if (definition.category === '数据可视化') return DataBoard
  if (definition.category === '表单交互') return EditPen
  return Tickets
}

function resetEditor() {
  Object.assign(draft, createComponentAssetDraft(props.initialValue))
  currentStep.value = props.mode === 'edit' && draft.sourceKey ? 1 : 0
  componentKeyword.value = ''
  activeCatalogCategory.value = 'all'
  previewTab.value = 'markdown'
}

function selectComponent(componentKey: string) {
  if (draft.sourceKey === componentKey) return
  bindApplicationComponent(draft, componentKey)
}

function goNext() {
  const error = validateComponentAssetDraft(draft, currentStep.value > 0)
  if (error) {
    ElMessage.warning(error)
    return
  }
  currentStep.value = Math.min(currentStep.value + 1, 2)
}

function goBack() {
  currentStep.value = Math.max(currentStep.value - 1, 0)
}

function submitAsset() {
  const error = validateComponentAssetDraft(draft, true)
  if (error) {
    ElMessage.warning(error)
    return
  }
  emit('submit', toComponentAssetSubmission(draft))
}

async function copyText(value: string, message: string) {
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(message)
  }
  catch {
    ElMessage.error('复制失败，请手动选择内容')
  }
}

watch(() => props.modelValue, (visible) => {
  if (visible) resetEditor()
})
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    class="component-asset-editor-dialog"
    modal-class="component-asset-editor-mask"
    width="min(1180px, calc(100vw - 32px))"
    top="4vh"
    destroy-on-close
    :close-on-click-modal="false"
    @closed="emit('closed')"
  >
    <template #header>
      <div class="component-asset-editor__header">
        <span class="component-asset-editor__header-icon" aria-hidden="true">
          <el-icon><Setting /></el-icon>
        </span>
        <div>
          <h2>{{ dialogTitle }}</h2>
          <p>从 Application 源组件出发，配置参数契约并生成可同步的知识文档。</p>
        </div>
        <el-tag effect="plain" round>资产化流程</el-tag>
      </div>
    </template>

    <div class="component-asset-editor__steps" aria-label="组件资产创建步骤">
      <el-steps :active="currentStep" simple finish-status="success">
        <el-step title="选择源组件" />
        <el-step title="配置资产" />
        <el-step title="生成文档" />
      </el-steps>
    </div>

    <div class="component-asset-editor__body">
      <main class="component-asset-editor__main">
        <section v-if="currentStep === 0" class="component-asset-step" aria-labelledby="component-source-title">
          <div class="component-asset-step__heading">
            <div>
              <span class="component-asset-step__index">01</span>
              <h3 id="component-source-title">选择 Application 组件</h3>
              <p>清单只暴露可独立使用的业务渲染器，不包含内部子组件。</p>
            </div>
            <el-input v-model="componentKeyword" clearable placeholder="搜索名称、Key 或源码路径">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
          </div>

          <div class="component-catalog-filter" role="group" aria-label="组件分类">
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
              :class="['component-catalog-card', { 'is-selected': draft.sourceKey === component.key }]"
              :aria-pressed="draft.sourceKey === component.key"
              @click="selectComponent(component.key)"
            >
              <span class="component-catalog-card__icon" aria-hidden="true">
                <el-icon><component :is="componentIcon(component)" /></el-icon>
              </span>
              <span class="component-catalog-card__content">
                <span class="component-catalog-card__topline">
                  <strong>{{ component.name }}</strong>
                  <el-icon v-if="draft.sourceKey === component.key" class="component-catalog-card__check"><Check /></el-icon>
                </span>
                <code>{{ component.key }}</code>
                <span class="component-catalog-card__description">{{ component.description }}</span>
                <span class="component-catalog-card__meta">
                  <span>{{ component.category }}</span>
                  <span>{{ component.parameters.length }} 个参数</span>
                  <span>v{{ component.version }}</span>
                </span>
                <small>{{ component.sourcePath }}</small>
              </span>
            </button>
          </div>
          <el-empty v-else description="没有匹配的 Application 组件" :image-size="72" />
        </section>

        <section v-else-if="currentStep === 1" class="component-asset-step" aria-labelledby="component-config-title">
          <div class="component-asset-step__heading component-asset-step__heading--compact">
            <div>
              <span class="component-asset-step__index">02</span>
              <h3 id="component-config-title">配置资产语义与参数</h3>
              <p>定义这个组件在系统中如何被识别、如何使用以及对外暴露哪些参数。</p>
            </div>
          </div>

          <div class="component-asset-panel">
            <div class="component-asset-panel__title">
              <span><el-icon><EditPen /></el-icon></span>
              <div><h4>基础信息</h4><p>对应后端组件配置的核心身份。</p></div>
            </div>
            <el-form label-position="top" class="component-asset-form">
              <div class="component-asset-form__grid">
                <el-form-item label="资产标识" required>
                  <el-input v-model="draft.key" placeholder="例如 list-main-layout" />
                </el-form-item>
                <el-form-item label="资产名称" required>
                  <el-input v-model="draft.name" placeholder="请输入资产名称" />
                </el-form-item>
                <el-form-item label="业务分类">
                  <el-select v-model="draft.category" allow-create filterable default-first-option placeholder="输入或选择分类">
                    <el-option v-for="option in assetCategoryOptions" :key="option.value" :label="option.label" :value="option.value" />
                  </el-select>
                </el-form-item>
                <el-form-item label="资产状态">
                  <el-select v-model="draft.status">
                    <el-option label="草稿" :value="1" />
                    <el-option label="已发布" :value="2" />
                    <el-option label="已停用" :value="3" />
                  </el-select>
                </el-form-item>
                <el-form-item label="负责人">
                  <el-input v-model="draft.owner" placeholder="资产维护人或团队" />
                </el-form-item>
                <el-form-item label="标签">
                  <el-select v-model="draft.tags" multiple allow-create filterable default-first-option placeholder="输入后回车添加标签" />
                </el-form-item>
              </div>
              <el-form-item label="能力说明" required>
                <el-input v-model="draft.summary" type="textarea" :rows="3" placeholder="说明组件能力边界与输出" />
              </el-form-item>
              <div class="component-asset-form__grid component-asset-form__grid--textareas">
                <el-form-item label="适用场景">
                  <el-input v-model="draft.useCases" type="textarea" :rows="4" placeholder="每行一个场景" />
                </el-form-item>
                <el-form-item label="使用指引">
                  <el-input v-model="draft.usageGuide" type="textarea" :rows="4" placeholder="说明接入步骤和数据责任" />
                </el-form-item>
                <el-form-item label="限制与注意事项">
                  <el-input v-model="draft.limitations" type="textarea" :rows="4" placeholder="补充使用限制、兼容性或风险" />
                </el-form-item>
                <el-form-item label="补充说明">
                  <el-input v-model="draft.notes" type="textarea" :rows="4" placeholder="可选，会追加到最终 Markdown 文档" />
                </el-form-item>
              </div>
            </el-form>
          </div>

          <div class="component-asset-panel">
            <div class="component-asset-panel__title component-asset-panel__title--between">
              <div class="component-asset-panel__title-group">
                <span><el-icon><Setting /></el-icon></span>
                <div><h4>参数策略</h4><p>必填参数固定纳入，可选参数可按资产场景控制。</p></div>
              </div>
              <el-tag effect="plain">{{ enabledParameterCount }} / {{ selectedDefinition?.parameters.length || 0 }} 已纳入</el-tag>
            </div>

            <div class="component-parameter-list">
              <article v-for="parameter in selectedDefinition?.parameters" :key="parameter.key" class="component-parameter-card">
                <div class="component-parameter-card__heading">
                  <div>
                    <strong>{{ parameter.label }}</strong>
                    <code>{{ parameter.key }}</code>
                    <el-tag v-if="parameter.required" size="small" type="danger" effect="plain">必填</el-tag>
                    <el-tag v-else size="small" effect="plain">可选</el-tag>
                  </div>
                  <div class="component-parameter-card__toggle">
                    <span>纳入资产</span>
                    <el-switch v-model="draft.parameters[parameter.key].enabled" :disabled="parameter.required" />
                  </div>
                </div>
                <div v-if="draft.parameters[parameter.key].enabled" class="component-parameter-card__body">
                  <div class="component-parameter-card__value">
                    <label>默认值 <small>{{ parameter.type }}</small></label>
                    <el-switch
                      v-if="parameter.control === 'boolean'"
                      v-model="draft.parameters[parameter.key].value"
                      inline-prompt
                      active-text="true"
                      inactive-text="false"
                    />
                    <el-input-number
                      v-else-if="parameter.control === 'number'"
                      v-model="draft.parameters[parameter.key].value"
                      controls-position="right"
                    />
                    <el-input
                      v-else-if="parameter.control === 'text'"
                      v-model="draft.parameters[parameter.key].value"
                      placeholder="请输入默认值"
                    />
                    <el-input
                      v-else
                      v-model="draft.parameters[parameter.key].value"
                      type="textarea"
                      :rows="5"
                      spellcheck="false"
                      placeholder="请输入合法 JSON"
                    />
                  </div>
                  <div class="component-parameter-card__description">
                    <label>参数说明</label>
                    <el-input v-model="draft.parameters[parameter.key].description" type="textarea" :rows="3" />
                  </div>
                </div>
              </article>
            </div>
          </div>
        </section>

        <section v-else class="component-asset-step" aria-labelledby="component-document-title">
          <div class="component-asset-step__heading component-asset-step__heading--compact">
            <div>
              <span class="component-asset-step__index">03</span>
              <h3 id="component-document-title">生成文档与知识资产</h3>
              <p>将组件身份、参数契约、使用说明和示例合并为一份可检索文档。</p>
            </div>
          </div>

          <div class="component-sync-flow" aria-label="资产同步流程">
            <div class="component-sync-flow__node is-ready">
              <span><el-icon><DataBoard /></el-icon></span>
              <div><strong>Application 定义</strong><small>已读取</small></div>
            </div>
            <el-icon class="component-sync-flow__arrow"><ArrowRightBold /></el-icon>
            <div class="component-sync-flow__node is-ready">
              <span><el-icon><Setting /></el-icon></span>
              <div><strong>后端组件配置</strong><small>本次保存</small></div>
            </div>
            <el-icon class="component-sync-flow__arrow"><ArrowRightBold /></el-icon>
            <div class="component-sync-flow__node is-pending">
              <span><el-icon><Connection /></el-icon></span>
              <div><strong>知识库文档</strong><small>待后端同步</small></div>
            </div>
          </div>

          <el-alert
            title="当前会将完整 Markdown 和配置 JSON 保存到组件配置；真正写入知识库将在后端同步接口接入后执行。"
            type="info"
            show-icon
            :closable="false"
          />

          <div class="component-asset-panel component-asset-panel--knowledge">
            <div class="component-asset-panel__title">
              <span><el-icon><Connection /></el-icon></span>
              <div><h4>知识资产定位</h4><p>预先定义文档目标，后续可直接映射到后端字段。</p></div>
            </div>
            <div class="component-knowledge-grid">
              <el-form-item label="生成知识库文档">
                <el-switch v-model="draft.generateKnowledgeDocument" active-text="是" inactive-text="否" />
              </el-form-item>
              <el-form-item label="目标知识库编码">
                <el-input v-model="draft.knowledgeBaseCode" :disabled="!draft.generateKnowledgeDocument" placeholder="system-component-assets" />
              </el-form-item>
              <el-form-item label="文档编码">
                <el-input :model-value="documentCode" readonly />
              </el-form-item>
            </div>
          </div>

          <div class="component-document-preview">
            <div class="component-document-preview__toolbar">
              <el-tabs v-model="previewTab">
                <el-tab-pane label="Markdown 资产文档" name="markdown" />
                <el-tab-pane label="配置 JSON" name="json" />
              </el-tabs>
              <el-button plain @click="copyText(previewTab === 'markdown' ? generatedDocument : generatedExample, '内容已复制')">
                复制内容
              </el-button>
            </div>
            <AppCodeEditor
              v-if="previewTab === 'markdown'"
              :model-value="generatedDocument"
              format="markdown"
              readonly
              :show-format-switcher="false"
              toolbar-label="Markdown"
              min-height="420px"
            />
            <AppCodeEditor
              v-else
              :model-value="generatedExample"
              format="json"
              readonly
              :show-format-switcher="false"
              toolbar-label="JSON"
              min-height="420px"
            />
          </div>
        </section>
      </main>

      <aside class="component-asset-preview" aria-label="资产实时预览">
        <div class="component-asset-preview__eyebrow">实时资产包</div>
        <div class="component-asset-preview__identity">
          <span><el-icon><component :is="selectedDefinition ? componentIcon(selectedDefinition) : Setting" /></el-icon></span>
          <div>
            <strong>{{ draft.name || selectedDefinition?.name || '待选择组件' }}</strong>
            <code>{{ draft.key || selectedDefinition?.key || 'component-key' }}</code>
          </div>
        </div>
        <p class="component-asset-preview__summary">
          {{ draft.summary || '选择 Application 组件后，将在这里预览它的能力说明。' }}
        </p>

        <div class="component-asset-preview__progress">
          <div><span>资产完整度</span><strong>{{ completionPercentage }}%</strong></div>
          <el-progress :percentage="completionPercentage" :show-text="false" :stroke-width="6" />
        </div>

        <dl class="component-asset-preview__facts">
          <div><dt>源组件</dt><dd>{{ selectedDefinition?.key || '未绑定' }}</dd></div>
          <div><dt>源码版本</dt><dd>{{ selectedDefinition ? `v${selectedDefinition.version}` : '-' }}</dd></div>
          <div><dt>参数契约</dt><dd>{{ enabledParameterCount }} 项</dd></div>
          <div><dt>文档规模</dt><dd>{{ documentCharacterCount.toLocaleString() }} 字符</dd></div>
          <div><dt>目标知识库</dt><dd>{{ draft.generateKnowledgeDocument ? (draft.knowledgeBaseCode || '待指定') : '不生成' }}</dd></div>
        </dl>

        <div class="component-asset-preview__checklist">
          <div :class="{ 'is-done': Boolean(selectedDefinition) }"><span><el-icon><Check /></el-icon></span>绑定 Application 定义</div>
          <div :class="{ 'is-done': Boolean(draft.key && draft.name && draft.summary) }"><span><el-icon><Check /></el-icon></span>补全资产语义</div>
          <div :class="{ 'is-done': currentStep === 2 }"><span><el-icon><Check /></el-icon></span>生成知识文档</div>
        </div>

        <div class="component-asset-preview__notice">
          <el-icon><Connection /></el-icon>
          <span>知识库同步接口待后端接入</span>
        </div>
      </aside>
    </div>

    <template #footer>
      <div class="component-asset-editor__footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <div>
          <el-button v-if="currentStep > 0" :disabled="submitting" @click="goBack">
            <el-icon><ArrowLeftBold /></el-icon>
            上一步
          </el-button>
          <el-button v-if="currentStep < 2" type="primary" @click="goNext">
            下一步
            <el-icon><ArrowRightBold /></el-icon>
          </el-button>
          <el-button v-else type="primary" :loading="submitting" @click="submitAsset">
            <el-icon><Check /></el-icon>
            {{ mode === 'create' ? '创建并生成资产' : '保存资产变更' }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style src="../styles/component-asset-editor.scss"></style>
