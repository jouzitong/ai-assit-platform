<script setup lang="ts">
import { Check, EditPen, Link, Plus, RefreshRight, Search, UploadFilled } from '@element-plus/icons-vue'
import { computed, ref } from 'vue'
import type { CatalogStatus, VirtualDataId } from '../../api/virtualData'
import { catalogStatusLabel, catalogStatusOptions, catalogStatusType } from '../data/options'
import type { VirtualEntitySummary } from '../data/types'

const props = defineProps<{
  rows: VirtualEntitySummary[]
  loading?: boolean
}>()

const emit = defineEmits<{
  initialize: []
  refresh: []
  openEntity: [id: VirtualDataId]
  validateEntity: [id: VirtualDataId]
  publishEntity: [id: VirtualDataId]
  openCanvas: []
}>()

const keyword = ref('')
const sourceKey = ref('')
const status = ref<CatalogStatus | ''>('')

const sourceOptions = computed(() => Array.from(new Set(props.rows.flatMap(row => row.sources))).sort())
const filteredRows = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  return props.rows.filter((row) => {
    const keywordMatched = !normalized || `${row.entityName || ''} ${row.entityCode || ''} ${row.physicalTables.join(' ')}`.toLowerCase().includes(normalized)
    const sourceMatched = !sourceKey.value || row.sources.includes(sourceKey.value)
    const statusMatched = !status.value || row.status === status.value
    return keywordMatched && sourceMatched && statusMatched
  })
})

const stats = computed(() => ({
  total: props.rows.length,
  published: props.rows.filter(row => row.status === 1).length,
  sources: new Set(props.rows.flatMap(row => row.sources)).size,
  relations: Math.floor(props.rows.reduce((sum, row) => sum + row.relationCount, 0) / 2),
}))
</script>

<template>
  <section class="virtual-catalog">
    <div class="virtual-catalog__stats" aria-label="虚拟表统计">
      <article><span>虚拟表</span><strong>{{ stats.total }}</strong><small>统一业务实体</small></article>
      <article><span>已发布</span><strong>{{ stats.published }}</strong><small>可参与执行计划</small></article>
      <article><span>接入数据源</span><strong>{{ stats.sources }}</strong><small>物理映射来源</small></article>
      <article><span>字段关联</span><strong>{{ stats.relations }}</strong><small>跨表逻辑关系</small></article>
    </div>

    <section class="virtual-catalog__panel">
      <header class="virtual-catalog__toolbar">
        <div class="virtual-catalog__filters">
          <el-input v-model="keyword" clearable placeholder="搜索名称、编码或物理表" aria-label="搜索虚拟表">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="sourceKey" clearable placeholder="全部数据源" aria-label="按数据源筛选">
            <el-option v-for="source in sourceOptions" :key="source" :label="source" :value="source" />
          </el-select>
          <el-select v-model="status" clearable placeholder="全部状态" aria-label="按发布状态筛选">
            <el-option v-for="option in catalogStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </div>
        <div class="virtual-catalog__actions">
          <el-button :icon="Link" @click="emit('openCanvas')">关系画布</el-button>
          <el-button :icon="RefreshRight" :loading="loading" @click="emit('refresh')">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="emit('initialize')">从数据源初始化</el-button>
        </div>
      </header>

      <el-table v-loading="loading" :data="filteredRows" row-key="id" class="virtual-catalog__table" height="100%">
        <el-table-column label="虚拟表" min-width="250" fixed>
          <template #default="{ row }">
            <div class="virtual-catalog__identity">
              <strong>{{ row.entityName || row.entityCode }}</strong>
              <code>{{ row.entityCode }}</code>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="物理映射" min-width="260">
          <template #default="{ row }">
            <div class="virtual-catalog__bindings">
              <span v-for="(table, index) in row.physicalTables" :key="`${table}-${index}`">
                <b>{{ row.sources[index] || row.sources[0] || '-' }}</b> / {{ table }}
              </span>
              <em v-if="!row.physicalTables.length">暂未绑定物理表</em>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="112">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="catalogStatusType(row.status)">{{ catalogStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="catalogVersion" label="版本" width="82" align="center" />
        <el-table-column label="模型规模" width="150">
          <template #default="{ row }">
            <span class="virtual-catalog__scale">{{ row.fieldCount }} 字段 · {{ row.relationCount }} 关联</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="272" fixed="right">
          <template #default="{ row }">
            <div class="virtual-catalog__row-actions">
              <el-button text type="primary" :icon="EditPen" @click="emit('openEntity', row.id)">模型配置</el-button>
              <el-button text :icon="Check" @click="emit('validateEntity', row.id)">校验</el-button>
              <el-button v-if="row.status !== 1" text type="success" :icon="UploadFilled" @click="emit('publishEntity', row.id)">发布</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <div class="virtual-catalog__empty">
            <strong>还没有虚拟表</strong>
            <span>从已有数据源和物理表初始化，字段与直连规则会自动生成。</span>
            <el-button type="primary" :icon="Plus" @click="emit('initialize')">开始初始化</el-button>
          </div>
        </template>
      </el-table>
    </section>
  </section>
</template>

<style scoped>
.virtual-catalog {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: var(--app-space-4);
  min-height: 0;
  height: 100%;
  padding: var(--app-space-4);
}

.virtual-catalog__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--app-space-3);
}

.virtual-catalog__stats article {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 2px var(--app-space-3);
  padding: var(--app-space-4);
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: var(--app-surface-gradient);
  box-shadow: var(--app-shadow-sm);
}

.virtual-catalog__stats span,
.virtual-catalog__stats small {
  color: var(--app-text-muted);
}

.virtual-catalog__stats strong {
  grid-row: 1 / span 2;
  grid-column: 2;
  align-self: center;
  color: var(--app-title);
  font-size: 26px;
}

.virtual-catalog__stats small {
  font-size: var(--app-font-size-caption);
}

.virtual-catalog__panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: var(--app-surface-solid);
  box-shadow: var(--app-shadow-sm);
}

.virtual-catalog__toolbar {
  display: flex;
  gap: var(--app-space-4);
  align-items: center;
  justify-content: space-between;
  padding: var(--app-space-3) var(--app-space-4);
  border-bottom: 1px solid var(--app-border);
}

.virtual-catalog__filters,
.virtual-catalog__actions,
.virtual-catalog__row-actions {
  display: flex;
  gap: var(--app-space-2);
  align-items: center;
}

.virtual-catalog__filters :deep(.el-input) {
  width: 260px;
}

.virtual-catalog__filters :deep(.el-select) {
  width: 150px;
}

.virtual-catalog__table {
  min-height: 0;
}

.virtual-catalog__identity strong,
.virtual-catalog__identity code {
  display: block;
}

.virtual-catalog__identity strong {
  color: var(--app-title);
}

.virtual-catalog__identity code,
.virtual-catalog__scale {
  margin-top: 3px;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.virtual-catalog__bindings {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.virtual-catalog__bindings span {
  padding: 3px 7px;
  border: 1px solid var(--app-border-subtle);
  border-radius: 6px;
  background: var(--app-surface-muted);
  color: var(--app-text-soft);
  font-size: var(--app-font-size-caption);
}

.virtual-catalog__bindings b {
  color: var(--app-accent);
  font-weight: 600;
}

.virtual-catalog__bindings em {
  color: var(--app-text-muted);
  font-style: normal;
}

.virtual-catalog__row-actions {
  gap: 0;
}

.virtual-catalog__empty {
  display: grid;
  justify-items: center;
  gap: var(--app-space-2);
  padding: 48px;
  color: var(--app-text-muted);
}

.virtual-catalog__empty strong {
  color: var(--app-title);
  font-size: var(--app-font-size-title-sm);
}

@media (max-width: 1120px) {
  .virtual-catalog__toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 760px) {
  .virtual-catalog__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .virtual-catalog__filters,
  .virtual-catalog__actions {
    flex-wrap: wrap;
  }
}
</style>
