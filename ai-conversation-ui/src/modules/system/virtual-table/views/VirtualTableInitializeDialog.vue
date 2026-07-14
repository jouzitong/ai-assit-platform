<script setup lang="ts">
import { RefreshRight, Select } from '@element-plus/icons-vue'
import { computed, ref, watch } from 'vue'
import type { DbDataSourceItem, DbTableMetaItem } from '../../api/dataSources'
import { buildVirtualEntityCode } from '../service/virtualTable'

const props = defineProps<{
  modelValue: boolean
  dataSources: DbDataSourceItem[]
  physicalTables: DbTableMetaItem[]
  existingCodes: string[]
  loading?: boolean
  syncing?: boolean
  submitting?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  sourceChange: [sourceKeys: string[]]
  sync: [sourceKeys: string[]]
  submit: [tables: DbTableMetaItem[]]
}>()

const sourceKeys = ref<string[]>([])
const selectedRows = ref<DbTableMetaItem[]>([])
const keyword = ref('')

const databaseSources = computed(() => props.dataSources.filter((source) => {
  if (source.enabled === false) return false
  const configType = 'configType' in (source.config || {}) ? String(source.config?.configType || '').toUpperCase() : ''
  if (configType) return configType === 'DATABASE'
  return Number(source.sourceType) === 1 || String(source.sourceType || '').toUpperCase() === 'DATABASE'
}))
const existingCodeSet = computed(() => new Set(props.existingCodes))
const filteredTables = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  if (!normalized) return props.physicalTables
  return props.physicalTables.filter(table => `${table.tableName || ''} ${table.tableComment || ''}`.toLowerCase().includes(normalized))
})

function virtualCode(table: DbTableMetaItem) {
  const sourceKey = table.sourceKey || (sourceKeys.value.length === 1 ? sourceKeys.value[0] : '')
  return sourceKey ? buildVirtualEntityCode(sourceKey, table.tableName) : '-'
}

function isInitialized(table: DbTableMetaItem) {
  return virtualCode(table) !== '-' && existingCodeSet.value.has(virtualCode(table))
}

function selectable(table: DbTableMetaItem) {
  return virtualCode(table) !== '-' && !isInitialized(table)
}

function handleSourceChange(value: string[]) {
  selectedRows.value = []
  keyword.value = ''
  emit('sourceChange', value || [])
}

function handleSubmit() {
  if (!sourceKeys.value.length || !selectedRows.value.length) return
  emit('submit', selectedRows.value)
}

watch(() => props.modelValue, (visible) => {
  if (!visible) return
  sourceKeys.value = []
  selectedRows.value = []
  keyword.value = ''
})
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    width="900px"
    title="从物理数据源初始化虚拟表"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="virtual-init">
      <section class="virtual-init__step">
        <span class="virtual-init__step-index">1</span>
        <div><strong>选择数据源</strong><p>读取已经同步到元数据中心的物理表。</p></div>
        <el-select v-model="sourceKeys" multiple filterable collapse-tags collapse-tags-tooltip placeholder="请选择数据库数据源" @change="handleSourceChange">
          <el-option
            v-for="source in databaseSources"
            :key="source.sourceKey || source.id"
            :label="`${source.sourceName || source.sourceKey} · ${source.sourceKey}`"
            :value="source.sourceKey"
          />
        </el-select>
        <el-button :icon="RefreshRight" :disabled="!sourceKeys.length" :loading="syncing" @click="emit('sync', sourceKeys)">同步物理元数据</el-button>
      </section>

      <section class="virtual-init__step virtual-init__step--tables">
        <span class="virtual-init__step-index">2</span>
        <div class="virtual-init__table-head">
          <div><strong>选择物理表</strong><p>默认虚拟表 name/key：<code>sourceKey_tableName</code>，已初始化的表不可重复选择。</p></div>
          <el-input v-model="keyword" clearable placeholder="搜索物理表" :disabled="!sourceKeys.length" />
        </div>
        <el-table
          class="virtual-init__table"
          v-loading="loading"
          :data="filteredTables"
          row-key="id"
          max-height="380"
          @selection-change="selectedRows = $event"
        >
          <el-table-column type="selection" width="46" :selectable="selectable" />
          <el-table-column prop="sourceKey" label="数据源" min-width="150" />
          <el-table-column prop="tableName" label="物理表" min-width="190" />
          <el-table-column prop="tableComment" label="说明" min-width="180" show-overflow-tooltip />
          <el-table-column label="生成的虚拟表编码" min-width="250">
            <template #default="{ row }"><code>{{ virtualCode(row) }}</code></template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="isInitialized(row)" type="success" size="small">已初始化</el-tag>
              <el-tag v-else type="info" size="small">可初始化</el-tag>
            </template>
          </el-table-column>
          <template #empty>
            <div class="virtual-init__empty">{{ sourceKeys.length ? '暂无已同步的物理表，可先执行“同步物理元数据”' : '请先选择数据源' }}</div>
          </template>
        </el-table>
      </section>

      <div class="virtual-init__summary">
        <el-icon><Select /></el-icon>
        已选择 <strong>{{ selectedRows.length }}</strong> 张表，来自 <strong>{{ sourceKeys.length }}</strong> 个数据源。初始化会创建虚拟字段、主绑定和默认 identity 双向转换规则。
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!sourceKeys.length || !selectedRows.length" @click="handleSubmit">
        初始化 {{ selectedRows.length || '' }} 张虚拟表
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.virtual-init {
  display: grid;
  gap: var(--app-space-4);
}

.virtual-init__step {
  display: grid;
  grid-template-columns: 30px minmax(180px, 1fr) 280px auto;
  gap: var(--app-space-3);
  align-items: center;
}

.virtual-init__step--tables {
  grid-template-columns: 30px minmax(0, 1fr);
  align-items: start;
}

.virtual-init__step-index {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--app-accent);
  color: var(--system-primary-button-text);
  font-weight: 700;
}

.virtual-init strong {
  color: var(--app-title);
}

.virtual-init p {
  margin: 3px 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.virtual-init__table-head {
  grid-column: 2;
  display: flex;
  gap: var(--app-space-4);
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--app-space-3);
}

.virtual-init__table {
  grid-column: 2;
  width: 100%;
  min-width: 0;
}

.virtual-init__table-head :deep(.el-input) {
  width: 220px;
}

.virtual-init__summary {
  display: flex;
  gap: var(--app-space-2);
  align-items: center;
  padding: var(--app-space-3);
  border: 1px solid var(--app-accent-border);
  border-radius: 8px;
  background: var(--app-accent-bg);
  color: var(--app-accent);
  font-size: var(--app-font-size-caption);
}

.virtual-init__empty {
  padding: var(--app-space-6);
  color: var(--app-text-muted);
}

@media (max-width: 760px) {
  .virtual-init__step {
    grid-template-columns: 30px minmax(0, 1fr);
  }

  .virtual-init__step > :deep(.el-select),
  .virtual-init__step > :deep(.el-button) {
    grid-column: 2;
    width: 100%;
  }

  .virtual-init__table-head {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
