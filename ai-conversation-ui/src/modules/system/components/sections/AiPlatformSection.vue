<script setup lang="ts">
import { RefreshRight, Search } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import {
  searchAiFlowSkills,
  searchAiKbStores,
  searchAiModelManages,
  type AiFlowSkillItem,
  type AiKbStoreItem,
  type AiModelManageItem,
} from '../../api/aiPlatform'

type PlatformTab = 'model' | 'kb' | 'skill' | 'tool'

const activeTab = ref<PlatformTab>('model')
const keyword = ref('')
const pageSize = ref(20)
const currentPage = ref(1)
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const modelRecords = ref<AiModelManageItem[]>([])
const kbRecords = ref<AiKbStoreItem[]>([])
const skillRecords = ref<AiFlowSkillItem[]>([])

const pageSizeOptions = [10, 20, 50, 100, 200, 500]

const tabOptions = [
  { key: 'model' as const, label: '模型管理' },
  { key: 'kb' as const, label: '知识库管理' },
  { key: 'skill' as const, label: 'Skill 管理' },
  { key: 'tool' as const, label: 'Tool 管理' },
]

const isToolTab = computed(() => activeTab.value === 'tool')
const currentTabLabel = computed(() => {
  switch (activeTab.value) {
    case 'model':
      return '模型'
    case 'kb':
      return '知识库'
    case 'skill':
      return 'Skill'
    default:
      return 'Tool'
  }
})
const currentSearchPlaceholder = computed(() => {
  switch (activeTab.value) {
    case 'model':
      return '搜索名称 / 编码 / Provider'
    case 'kb':
      return '搜索名称 / 编码 / Provider KB'
    case 'skill':
      return '搜索 Skill 名称 / 编码 / 类型'
    default:
      return 'Tool 管理接口待接入'
  }
})

const currentCards = computed(() => {
  if (activeTab.value === 'model') {
    return modelRecords.value.map((item) => ({
      id: item.id,
      title: item.modelName || item.modelCode || '未命名模型',
      code: item.modelCode || '-',
      tags: [
        item.providerName || item.providerCode || '未配置 Provider',
        item.enabled === false ? '停用' : '启用',
      ],
      summary: item.apiModel || item.baseUrl || '暂无模型说明',
      extras: [
        { label: 'Provider', value: item.providerCode || '-' },
        { label: 'Base URL', value: item.baseUrl || '-' },
        { label: '密钥', value: item.apiKeyMasked || '未配置' },
        { label: '更新时间', value: formatDateTime(item.updateTime || item.createTime) },
      ],
    }))
  }

  if (activeTab.value === 'skill') {
    return skillRecords.value.map((item) => ({
      id: item.id,
      title: item.name || item.code || '未命名 Skill',
      code: item.code || '-',
      tags: [
        item.type || '未配置类型',
        ...(item.config?.supportedPhases || []).slice(0, 2),
        item.enabled === false ? '停用' : '启用',
      ],
      summary: item.config?.summary || '暂无 Skill 摘要',
      extras: [
        { label: '类型', value: item.type || '-' },
        { label: '支持阶段', value: (item.config?.supportedPhases || []).join(', ') || '-' },
        { label: '状态', value: item.enabled === false ? '停用' : '启用' },
        { label: '更新时间', value: formatDateTime(item.updateTime || item.createTime) },
      ],
    }))
  }

  if (activeTab.value === 'tool') {
    return []
  }

  return kbRecords.value.map((item) => ({
    id: item.id,
    title: item.kbName || item.kbCode || '未命名知识库',
    code: item.kbCode || '-',
    tags: [
      ...(item.tags || []).slice(0, 3),
      item.enabled === false ? '停用' : '启用',
    ],
    summary: item.url || item.providerKbId || '暂无知识库说明',
    extras: [
      { label: 'Provider KB', value: item.providerKbId || '-' },
      { label: '地址', value: item.url || '-' },
      { label: '标签数', value: String(item.tags?.length || 0) },
      { label: '更新时间', value: formatDateTime(item.updateTime || item.createTime) },
    ],
  }))
})

function resolveTotal(payloadTotal?: number, fallback = 0) {
  const numericTotal = Number(payloadTotal)
  return Number.isFinite(numericTotal) ? numericTotal : fallback
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

async function loadData() {
  loading.value = true
  errorMessage.value = ''
  try {
    if (activeTab.value === 'tool') {
      total.value = 0
      return
    }

    if (activeTab.value === 'model') {
      const payload = await searchAiModelManages({
        page: currentPage.value,
        size: pageSize.value,
        keyword: keyword.value.trim() || undefined,
      })
      modelRecords.value = payload?.list ?? []
      total.value = resolveTotal(payload?.pageInfo?.total, modelRecords.value.length)
      return
    }

    if (activeTab.value === 'skill') {
      const payload = await searchAiFlowSkills({
        page: currentPage.value,
        size: pageSize.value,
        keyword: keyword.value.trim() || undefined,
      })
      skillRecords.value = payload?.list ?? []
      total.value = resolveTotal(payload?.pageInfo?.total, skillRecords.value.length)
      return
    }

    const payload = await searchAiKbStores({
      page: currentPage.value,
      size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
    })
    kbRecords.value = payload?.list ?? []
    total.value = resolveTotal(payload?.pageInfo?.total, kbRecords.value.length)
  }
  catch (error) {
    total.value = 0
    errorMessage.value = error instanceof Error ? error.message : 'AI 平台数据加载失败'
  }
  finally {
    loading.value = false
  }
}

async function handleChangeTab(tab: PlatformTab) {
  if (activeTab.value === tab) {
    return
  }
  activeTab.value = tab
  keyword.value = ''
  currentPage.value = 1
  await loadData()
}

async function handleSearch() {
  currentPage.value = 1
  await loadData()
}

async function handleRefresh() {
  await loadData()
}

async function handleCurrentPageChange(page: number) {
  currentPage.value = page
  await loadData()
}

async function handlePageSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  await loadData()
}

onMounted(() => {
  void loadData()
})
</script>

<template>
  <section class="ai-platform-page">
    <div class="ai-platform-shell">
      <header class="ai-platform-shell__header">
        <div class="ai-platform-shell__tabs">
          <el-tag
            v-for="tab in tabOptions"
            :key="tab.key"
            :type="activeTab === tab.key ? 'primary' : 'info'"
            effect="plain"
            class="ai-platform-shell__tab"
            @click="handleChangeTab(tab.key)"
          >
            {{ tab.label }}
          </el-tag>
        </div>
        <div class="ai-platform-shell__tools">
          <el-input
            v-model="keyword"
            :placeholder="currentSearchPlaceholder"
            :disabled="isToolTab"
            clearable
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button plain :loading="loading" :disabled="isToolTab" @click="handleRefresh">
            <el-icon><RefreshRight /></el-icon>
            刷新
          </el-button>
        </div>
      </header>

      <main class="ai-platform-shell__main">
        <div v-if="errorMessage" class="ai-platform-shell__state ai-platform-shell__state--error">
          {{ errorMessage }}
        </div>
        <div v-else-if="loading" class="ai-platform-shell__state">
          正在加载{{ currentTabLabel }}列表...
        </div>
        <div v-else-if="activeTab === 'tool'" class="ai-platform-shell__state">
          Tool 管理接口暂未接入，当前先保留标签入口。
        </div>
        <div v-else-if="!currentCards.length" class="ai-platform-shell__state">
          当前没有{{ currentTabLabel }}数据
        </div>
        <div v-else class="ai-platform-shell__grid">
          <article v-for="card in currentCards" :key="card.id" class="ai-platform-card">
            <div class="ai-platform-card__head">
              <div>
                <h3>{{ card.title }}</h3>
                <p>{{ card.code }}</p>
              </div>
              <div class="ai-platform-card__tags">
                <el-tag
                  v-for="tag in card.tags.filter(Boolean)"
                  :key="tag"
                  size="small"
                  effect="plain"
                >
                  {{ tag }}
                </el-tag>
              </div>
            </div>
            <div class="ai-platform-card__summary">{{ card.summary }}</div>
            <div class="ai-platform-card__meta">
              <div v-for="extra in card.extras" :key="extra.label" class="ai-platform-card__meta-item">
                <span>{{ extra.label }}</span>
                <strong>{{ extra.value }}</strong>
              </div>
            </div>
          </article>
        </div>
      </main>

      <footer class="ai-platform-shell__footer">
        <div class="ai-platform-shell__footer-total">Total {{ total }}</div>
        <el-pagination
          v-if="!isToolTab"
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="pageSizeOptions"
          :pager-count="5"
          layout="sizes, prev, pager, next"
          :total="total"
          @current-change="handleCurrentPageChange"
          @size-change="handlePageSizeChange"
        />
        <span v-else class="ai-platform-shell__footer-hint">待接入 Tool 管理接口</span>
      </footer>
    </div>
  </section>
</template>

<style scoped>
.ai-platform-page {
  display: flex;
  flex: 1;
  min-height: 0;
}

.ai-platform-shell {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  flex: 1;
  min-height: 0;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
  overflow: hidden;
}

.ai-platform-shell__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  border-bottom: 1px solid #eef2f7;
}

.ai-platform-shell__tabs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-platform-shell__tab {
  cursor: pointer;
}

.ai-platform-shell__tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-platform-shell__tools :deep(.el-input) {
  width: 260px;
}

.ai-platform-shell__main {
  min-height: 0;
  padding: 14px 16px;
  background: #f8fafc;
  overflow-y: auto;
}

.ai-platform-shell__state {
  display: grid;
  place-items: center;
  min-height: 260px;
  color: #6b7280;
  font-size: 13px;
}

.ai-platform-shell__state--error {
  color: #dc2626;
}

.ai-platform-shell__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
  align-content: start;
}

.ai-platform-card {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: #fff;
}

.ai-platform-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.ai-platform-card__head h3 {
  margin: 0;
  color: #111827;
  font-size: 15px;
}

.ai-platform-card__head p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
}

.ai-platform-card__tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.ai-platform-card__summary {
  color: #334155;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-all;
}

.ai-platform-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.ai-platform-card__meta-item {
  display: grid;
  gap: 4px;
}

.ai-platform-card__meta-item span {
  color: #94a3b8;
  font-size: 12px;
}

.ai-platform-card__meta-item strong {
  color: #111827;
  font-size: 12px;
  word-break: break-all;
}

.ai-platform-shell__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: 44px;
  padding: 0 16px;
  border-top: 1px solid #eef2f7;
  background: #fff;
}

.ai-platform-shell__footer-total {
  color: #6b7280;
  font-size: 12px;
  white-space: nowrap;
}

.ai-platform-shell__footer-hint {
  color: #94a3b8;
  font-size: 12px;
}

@media (max-width: 960px) {
  .ai-platform-shell__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .ai-platform-shell__tools {
    width: 100%;
    flex-wrap: wrap;
  }

  .ai-platform-shell__tools :deep(.el-input) {
    width: 100%;
  }

  .ai-platform-card__meta {
    grid-template-columns: 1fr;
  }

  .ai-platform-shell__footer {
    height: auto;
    padding: 10px 12px;
    flex-wrap: wrap;
  }
}
</style>
