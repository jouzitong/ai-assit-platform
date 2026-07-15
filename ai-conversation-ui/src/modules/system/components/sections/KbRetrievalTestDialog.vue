<script setup lang="ts">
import { RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'
import {
  testAiKbRetrieval,
  type AiKbRetrievalTestItem,
  type AiKbStoreItem,
} from '../../api/aiPlatform'

const props = defineProps<{
  modelValue: boolean
  knowledgeBase: AiKbStoreItem | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const DEFAULT_FORM = {
  query: '',
  similarityThreshold: 0.2,
  vectorSimilarityWeight: 0.3,
  pageSize: 10,
  retrievalTopK: 1024,
  rerankId: '',
  keyword: false,
  highlight: true,
  useKg: false,
  tocEnhance: false,
  documentIdsText: '',
  crossLanguages: [] as string[],
  metadataConditionText: '',
}

const form = reactive({ ...DEFAULT_FORM, crossLanguages: [] as string[] })
const loading = ref(false)
const executed = ref(false)
const durationMs = ref<number | null>(null)
const errorMessage = ref('')
const resultItems = ref<AiKbRetrievalTestItem[]>([])
const resultTotal = ref(0)
const activeAdvancedSections = ref<string[]>([])

const dialogVisible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const keywordSimilarityWeight = computed(() => Number((1 - form.vectorSimilarityWeight).toFixed(2)))

function resetState() {
  Object.assign(form, DEFAULT_FORM, { crossLanguages: [] })
  loading.value = false
  executed.value = false
  durationMs.value = null
  errorMessage.value = ''
  resultItems.value = []
  resultTotal.value = 0
  activeAdvancedSections.value = []
}

function normalizeText(value?: string) {
  return value?.trim() || ''
}

function parseTextList(value: string) {
  return [...new Set(value.split(/[\n,，]+/).map(item => item.trim()).filter(Boolean))]
}

function parseMetadataCondition() {
  const text = normalizeText(form.metadataConditionText)
  if (!text) {
    return null
  }
  const value = JSON.parse(text)
  if (!value || Array.isArray(value) || typeof value !== 'object') {
    throw new Error('元数据过滤条件必须是 JSON 对象')
  }
  return value as Record<string, unknown>
}

function validateForm() {
  if (!normalizeText(props.knowledgeBase?.kbCode)) {
    return '缺少知识库编码，无法执行检索测试'
  }
  if (!normalizeText(form.query)) {
    return '请输入检索内容'
  }
  if (form.similarityThreshold < 0 || form.similarityThreshold > 1) {
    return '相似度阈值必须在 0 到 1 之间'
  }
  if (form.vectorSimilarityWeight < 0 || form.vectorSimilarityWeight > 1) {
    return '向量相似度权重必须在 0 到 1 之间'
  }
  if (!Number.isInteger(form.pageSize) || form.pageSize <= 0) {
    return '返回数量必须是正整数'
  }
  if (!Number.isInteger(form.retrievalTopK) || form.retrievalTopK <= 0) {
    return '候选 Top-K 必须是正整数'
  }
  return ''
}

async function runRetrievalTest() {
  if (loading.value) {
    return
  }
  const validationError = validateForm()
  if (validationError) {
    ElMessage.error(validationError)
    return
  }

  let metadataCondition: Record<string, unknown> | null
  try {
    metadataCondition = parseMetadataCondition()
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '元数据过滤条件格式不正确')
    return
  }

  loading.value = true
  executed.value = true
  errorMessage.value = ''
  const startTime = performance.now()
  try {
    const result = await testAiKbRetrieval({
      kbId: normalizeText(props.knowledgeBase?.kbCode),
      query: normalizeText(form.query),
      topK: form.pageSize,
      page: 1,
      pageSize: form.pageSize,
      retrievalTopK: form.retrievalTopK,
      similarityThreshold: form.similarityThreshold,
      vectorSimilarityWeight: form.vectorSimilarityWeight,
      rerankId: normalizeText(form.rerankId) || undefined,
      keyword: form.keyword,
      highlight: form.highlight,
      useKg: form.useKg,
      tocEnhance: form.tocEnhance,
      documentIds: parseTextList(form.documentIdsText),
      crossLanguages: form.crossLanguages.map(item => item.trim()).filter(Boolean),
      metadataCondition,
      meta: { scene: 'kb-retrieval-test' },
    })
    resultItems.value = result?.items ?? []
    resultTotal.value = Number.isFinite(Number(result?.total)) ? Number(result?.total) : resultItems.value.length
  }
  catch (error) {
    resultItems.value = []
    resultTotal.value = 0
    errorMessage.value = error instanceof Error ? error.message : '知识库检索测试失败'
  }
  finally {
    durationMs.value = Math.max(1, Math.round(performance.now() - startTime))
    loading.value = false
  }
}

function metadataNumber(item: AiKbRetrievalTestItem, ...keys: string[]) {
  for (const key of keys) {
    const value = item.metadata?.[key]
    const numericValue = typeof value === 'number' ? value : Number(value)
    if (Number.isFinite(numericValue)) {
      return numericValue
    }
  }
  return null
}

function metadataText(item: AiKbRetrievalTestItem, ...keys: string[]) {
  for (const key of keys) {
    const value = item.metadata?.[key]
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
  }
  return ''
}

function formatScore(value?: number | null) {
  return typeof value === 'number' && Number.isFinite(value) ? `${(value * 100).toFixed(2)}%` : '-'
}

function resolveDocumentName(item: AiKbRetrievalTestItem) {
  return metadataText(item, 'document_keyword', 'doc_name', 'document_name', 'docnm_kwd')
    || item.documentId
    || '未知文档'
}

function plainContent(content?: string) {
  if (!content) {
    return ''
  }
  try {
    return new DOMParser().parseFromString(content, 'text/html').body.textContent || ''
  }
  catch {
    return content
  }
}

function metadataJson(item: AiKbRetrievalTestItem) {
  const metadata = { ...(item.metadata || {}) }
  delete metadata.content
  delete metadata.content_with_weight
  delete metadata.vector
  return JSON.stringify(metadata, null, 2)
}

watch(() => props.modelValue, (visible) => {
  if (visible) {
    resetState()
  }
})

watch(() => props.knowledgeBase?.kbCode, () => {
  if (props.modelValue) {
    resetState()
  }
})
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    class="kb-retrieval-test-dialog"
    :title="`检索测试 - ${knowledgeBase?.kbName || knowledgeBase?.kbCode || '知识库'}`"
    width="min(1080px, calc(100vw - 32px))"
    destroy-on-close
    :close-on-click-modal="!loading"
  >
    <div class="kb-retrieval-test">
      <aside class="kb-retrieval-test__settings">
        <div class="kb-retrieval-test__identity">
          <el-tag size="small" effect="plain">{{ knowledgeBase?.kbCode || '-' }}</el-tag>
          <span>{{ knowledgeBase?.providerKbId || '-' }}</span>
        </div>

        <el-form label-position="top" class="kb-retrieval-test__form" @submit.prevent="runRetrievalTest">
          <el-form-item label="检索内容" required>
            <el-input
              v-model="form.query"
              type="textarea"
              :rows="4"
              resize="vertical"
              placeholder="输入需要验证召回效果的问题或关键词"
              @keydown.ctrl.enter.prevent="runRetrievalTest"
              @keydown.meta.enter.prevent="runRetrievalTest"
            />
          </el-form-item>

          <el-form-item label="相似度阈值">
            <div class="kb-retrieval-test__slider-control">
              <el-slider v-model="form.similarityThreshold" :min="0" :max="1" :step="0.01" />
              <el-input-number
                v-model="form.similarityThreshold"
                :min="0"
                :max="1"
                :step="0.01"
                :precision="2"
                controls-position="right"
              />
            </div>
          </el-form-item>

          <el-form-item label="向量相似度权重">
            <div class="kb-retrieval-test__slider-control">
              <el-slider v-model="form.vectorSimilarityWeight" :min="0" :max="1" :step="0.01" />
              <el-input-number
                v-model="form.vectorSimilarityWeight"
                :min="0"
                :max="1"
                :step="0.01"
                :precision="2"
                controls-position="right"
              />
            </div>
            <div class="kb-retrieval-test__field-note">关键词相似度权重 {{ keywordSimilarityWeight.toFixed(2) }}</div>
          </el-form-item>

          <div class="kb-retrieval-test__number-grid">
            <el-form-item label="返回数量">
              <el-input-number v-model="form.pageSize" :min="1" :max="100" controls-position="right" />
            </el-form-item>
            <el-form-item label="候选 Top-K">
              <el-input-number v-model="form.retrievalTopK" :min="1" :max="10000" controls-position="right" />
            </el-form-item>
          </div>

          <el-form-item label="Rerank 模型">
            <el-input v-model="form.rerankId" clearable placeholder="可选，填写 RAGFlow Rerank 模型标识" />
          </el-form-item>

          <div class="kb-retrieval-test__switch-grid">
            <label><span>关键词增强</span><el-switch v-model="form.keyword" /></label>
            <label><span>命中高亮</span><el-switch v-model="form.highlight" /></label>
            <label><span>知识图谱</span><el-switch v-model="form.useKg" /></label>
            <label><span>目录增强</span><el-switch v-model="form.tocEnhance" /></label>
          </div>

          <el-collapse v-model="activeAdvancedSections" class="kb-retrieval-test__advanced">
            <el-collapse-item title="文档与元数据过滤" name="filters">
              <el-form-item label="Provider 文档 ID">
                <el-input
                  v-model="form.documentIdsText"
                  type="textarea"
                  :rows="2"
                  placeholder="多个文档 ID 使用逗号或换行分隔"
                />
              </el-form-item>
              <el-form-item label="跨语言检索">
                <el-select
                  v-model="form.crossLanguages"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  clearable
                  placeholder="输入语言并回车"
                />
              </el-form-item>
              <el-form-item label="metadata_condition">
                <el-input
                  v-model="form.metadataConditionText"
                  type="textarea"
                  :rows="5"
                  placeholder="{&#10;  &quot;logic&quot;: &quot;and&quot;,&#10;  &quot;conditions&quot;: []&#10;}"
                />
              </el-form-item>
            </el-collapse-item>
          </el-collapse>

          <el-button
            class="kb-retrieval-test__submit"
            type="primary"
            native-type="submit"
            :loading="loading"
            :disabled="!form.query.trim()"
          >
            <el-icon><Search /></el-icon>
            执行检索
          </el-button>
        </el-form>
      </aside>

      <section class="kb-retrieval-test__results">
        <header class="kb-retrieval-test__results-header">
          <div>
            <h3>检索结果</h3>
            <span v-if="executed && durationMs !== null">共 {{ resultTotal }} 条 · 当前 {{ resultItems.length }} 条 · {{ durationMs }}ms</span>
          </div>
          <el-button v-if="executed" circle plain :loading="loading" title="重新检索" @click="runRetrievalTest">
            <el-icon><RefreshRight /></el-icon>
          </el-button>
        </header>

        <div v-if="errorMessage" class="kb-retrieval-test__state kb-retrieval-test__state--error">
          {{ errorMessage }}
        </div>
        <div v-else-if="loading" class="kb-retrieval-test__state">
          正在检索...
        </div>
        <el-empty v-else-if="!executed" description="输入检索内容并执行测试" />
        <el-empty v-else-if="!resultItems.length" description="没有命中符合当前参数的 Chunk" />
        <el-scrollbar v-else class="kb-retrieval-test__result-scroll">
          <article v-for="(item, index) in resultItems" :key="`${item.documentId || 'chunk'}-${index}`" class="kb-retrieval-hit">
            <div class="kb-retrieval-hit__head">
              <div class="kb-retrieval-hit__document">
                <strong>{{ resolveDocumentName(item) }}</strong>
                <span>{{ item.documentId || '-' }}</span>
              </div>
              <span class="kb-retrieval-hit__rank">#{{ index + 1 }}</span>
            </div>
            <div class="kb-retrieval-hit__scores">
              <span>混合 {{ formatScore(item.score) }}</span>
              <span>向量 {{ formatScore(metadataNumber(item, 'vector_similarity')) }}</span>
              <span>关键词 {{ formatScore(metadataNumber(item, 'term_similarity')) }}</span>
            </div>
            <p class="kb-retrieval-hit__content">{{ plainContent(item.content) }}</p>
            <el-collapse class="kb-retrieval-hit__metadata">
              <el-collapse-item title="查看元数据" :name="index">
                <pre>{{ metadataJson(item) }}</pre>
              </el-collapse-item>
            </el-collapse>
          </article>
        </el-scrollbar>
      </section>
    </div>
  </el-dialog>
</template>

<style scoped>
:global(.kb-retrieval-test-dialog) {
  overflow: hidden;
  border: 1px solid var(--system-border);
  border-radius: 8px;
  background: var(--system-surface-strong);
}

:global(.kb-retrieval-test-dialog .el-dialog__header) {
  margin-right: 0;
  padding: 16px 20px;
  border-bottom: 1px solid var(--system-border-subtle);
}

:global(.kb-retrieval-test-dialog .el-dialog__body) {
  padding: 0;
}

.kb-retrieval-test {
  display: grid;
  grid-template-columns: minmax(320px, 380px) minmax(0, 1fr);
  min-height: min(680px, calc(100vh - 150px));
  max-height: calc(100vh - 150px);
  color: var(--system-text);
}

.kb-retrieval-test__settings {
  min-width: 0;
  padding: 16px 18px;
  border-right: 1px solid var(--system-border-subtle);
  background: var(--system-surface-muted);
  overflow-y: auto;
}

.kb-retrieval-test__identity {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
  margin-bottom: 14px;
  color: var(--system-text-muted);
  font-size: 12px;
}

.kb-retrieval-test__identity span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-retrieval-test__form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.kb-retrieval-test__form :deep(.el-form-item__label) {
  color: var(--system-text-soft);
}

.kb-retrieval-test__form :deep(.el-input__wrapper),
.kb-retrieval-test__form :deep(.el-textarea__inner),
.kb-retrieval-test__form :deep(.el-select__wrapper),
.kb-retrieval-test__form :deep(.el-input-number) {
  border-color: var(--system-border);
  background: var(--system-surface-solid);
  box-shadow: none;
}

.kb-retrieval-test__slider-control {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 112px;
  align-items: center;
  width: 100%;
  gap: 12px;
}

.kb-retrieval-test__slider-control :deep(.el-input-number),
.kb-retrieval-test__number-grid :deep(.el-input-number) {
  width: 100%;
}

.kb-retrieval-test__field-note {
  width: 100%;
  margin-top: 5px;
  color: var(--system-text-faint);
  font-size: 12px;
}

.kb-retrieval-test__number-grid,
.kb-retrieval-test__switch-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.kb-retrieval-test__switch-grid {
  margin-bottom: 14px;
}

.kb-retrieval-test__switch-grid label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 36px;
  gap: 8px;
  color: var(--system-text-soft);
  font-size: 13px;
}

.kb-retrieval-test__advanced {
  margin-bottom: 16px;
  border-color: var(--system-border-subtle);
}

.kb-retrieval-test__advanced :deep(.el-collapse-item__header),
.kb-retrieval-test__advanced :deep(.el-collapse-item__wrap) {
  background: transparent;
  color: var(--system-text-soft);
}

.kb-retrieval-test__advanced :deep(.el-select) {
  width: 100%;
}

.kb-retrieval-test__advanced :deep(.el-textarea__inner) {
  font-family: Menlo, Monaco, Consolas, 'Courier New', monospace;
}

.kb-retrieval-test__submit {
  width: 100%;
}

.kb-retrieval-test__results {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-width: 0;
  min-height: 0;
  background: var(--system-surface-solid);
}

.kb-retrieval-test__results-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 58px;
  padding: 10px 18px;
  border-bottom: 1px solid var(--system-border-subtle);
}

.kb-retrieval-test__results-header h3 {
  margin: 0;
  color: var(--system-title);
  font-size: 15px;
}

.kb-retrieval-test__results-header span {
  color: var(--system-text-muted);
  font-size: 12px;
}

.kb-retrieval-test__state {
  display: grid;
  place-items: center;
  min-height: 220px;
  padding: 24px;
  color: var(--system-text-muted);
  font-size: 13px;
  text-align: center;
}

.kb-retrieval-test__state--error {
  color: var(--system-danger);
}

.kb-retrieval-test__result-scroll {
  min-height: 0;
}

.kb-retrieval-hit {
  padding: 16px 18px;
  border-bottom: 1px solid var(--system-border-subtle);
}

.kb-retrieval-hit:last-child {
  border-bottom: 0;
}

.kb-retrieval-hit__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.kb-retrieval-hit__document {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.kb-retrieval-hit__document strong {
  color: var(--system-title);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.kb-retrieval-hit__document span,
.kb-retrieval-hit__rank {
  color: var(--system-text-faint);
  font-size: 11px;
  overflow-wrap: anywhere;
}

.kb-retrieval-hit__scores {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
  color: var(--system-accent-text);
  font-size: 12px;
}

.kb-retrieval-hit__content {
  margin: 10px 0 0;
  color: var(--system-text);
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.kb-retrieval-hit__metadata {
  margin-top: 8px;
  border: 0;
}

.kb-retrieval-hit__metadata :deep(.el-collapse-item__header),
.kb-retrieval-hit__metadata :deep(.el-collapse-item__wrap) {
  min-height: 32px;
  border: 0;
  background: transparent;
  color: var(--system-text-muted);
  font-size: 12px;
}

.kb-retrieval-hit__metadata pre {
  max-height: 220px;
  margin: 0;
  padding: 10px;
  border: 1px solid var(--system-border-subtle);
  border-radius: 6px;
  background: var(--system-surface-muted);
  color: var(--system-text-soft);
  font-family: Menlo, Monaco, Consolas, 'Courier New', monospace;
  font-size: 11px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow: auto;
}

@media (max-width: 860px) {
  .kb-retrieval-test {
    grid-template-columns: 1fr;
    min-height: 0;
    max-height: calc(100vh - 120px);
    overflow-y: auto;
  }

  .kb-retrieval-test__settings {
    border-right: 0;
    border-bottom: 1px solid var(--system-border-subtle);
    overflow: visible;
  }

  .kb-retrieval-test__results {
    min-height: 420px;
  }
}

@media (max-width: 520px) {
  .kb-retrieval-test__slider-control,
  .kb-retrieval-test__number-grid,
  .kb-retrieval-test__switch-grid {
    grid-template-columns: 1fr;
  }
}
</style>
