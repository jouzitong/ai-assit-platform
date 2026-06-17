<script setup>
import { computed, ref } from 'vue'
import ActionBar from '../../../../../components/commons/list/ActionBar.vue'
import AdvancedFilters from '../../../../../components/commons/list/AdvancedFilters.vue'
import DataListFooter from '../../../../../components/commons/list/DataListFooter.vue'
import DataTable from '../../../../../components/commons/list/DataTable.vue'
import FilterBar from '../../../../../components/commons/list/FilterBar.vue'
import FilterSummary from '../../../../../components/commons/list/FilterSummary.vue'
import HeaderBar from '../../../../../components/commons/list/HeaderBar.vue'
import ListCommonLayout from '../../../../../components/commons/list/ListCommonLayout.vue'
import Sidebar from '../../../../../components/commons/list/Sidebar.vue'
import StatsBar from '../../../../../components/commons/list/StatsBar.vue'

const sidebarCollapsed = ref(false)
const advancedVisible = ref(true)
const page = ref(1)
const pageSize = ref(10)
const activeSection = ref('workflow')
const sorts = ref([{ key: 'priority', type: 'desc' }])

const sidebarItems = [
  { key: 'workflow', label: '工作流', count: 8 },
  { key: 'node', label: '节点模板', count: 14 },
  { key: 'skill', label: '技能组件', count: 6 }
]

const filterSchema = ref([
  { key: 'keyword', label: '搜索', type: 'input', value: '', action: 'keyword-change', type_config: { placeholder: '搜索组件名 / 负责人', width: 220, clearable: true } },
  { key: 'status', label: '状态', type: 'select', value: '', action: 'status-change', type_config: { width: 150, clearable: true, options: [{ code: '', name: '全部状态' }, { code: 'online', name: '已上线' }, { code: 'draft', name: '草稿' }, { code: 'offline', name: '已下线' }] } },
  { key: 'owner', label: '负责人', type: 'select', value: '', action: 'owner-change', type_config: { width: 150, clearable: true, options: [{ code: '', name: '全部负责人' }, { code: '周同学', name: '周同学' }, { code: 'Athena Team', name: 'Athena Team' }, { code: 'Agent Lab', name: 'Agent Lab' }] } },
  { key: 'toggleAdvanced', label: '高级筛选', type: 'button', action: 'toggle-advanced', variant: 'ghost', type_config: { width: 110 } }
])

const advancedSchema = ref([
  { key: 'tag', label: '标签', type: 'input', value: '', action: 'tag-change', type_config: { placeholder: '输入标签', width: 220 } },
  { key: 'scene', label: '场景', type: 'select', value: '', action: 'scene-change', type_config: { width: 180, options: [{ code: '', name: '全部场景' }, { code: 'orchestration', name: '编排' }, { code: 'retrieval', name: '检索' }, { code: 'ui', name: '前端展示' }] } },
  { key: 'updatedAt', label: '更新时间', type: 'date', value: '', action: 'date-change', type_config: { width: 180 } }
])

const actionItems = [
  { key: 'create', label: '新建组件', type: 'primary', action: 'create' },
  { key: 'sync', label: '同步元数据', variant: 'ghost', action: 'sync' },
  { key: 'publish', label: '发布', variant: 'ghost', action: 'publish' },
  { key: 'archive', label: '归档', variant: 'ghost', action: 'archive' },
  { key: 'remove', label: '删除', variant: 'danger', action: 'remove' }
]

const statsItems = [
  { key: 'online', label: '已上线', value: 18 },
  { key: 'draft', label: '草稿', value: 7 },
  { key: 'owner', label: '负责人', value: 4 },
  { key: 'version', label: '本周变更', value: 12 }
]

const rows = [
  { id: 1, title: 'WorkflowList', owner: '周同学', status: 'online', priority: 'P1', scene: '编排', updatedAt: '2026-06-17', desc: '工作流列表页基础容器' },
  { id: 2, title: 'NodeCard', owner: 'Athena Team', status: 'draft', priority: 'P2', scene: '前端展示', updatedAt: '2026-06-16', desc: '节点卡片展示模型' },
  { id: 3, title: 'SkillShelf', owner: 'Agent Lab', status: 'online', priority: 'P0', scene: '检索', updatedAt: '2026-06-15', desc: '技能选择与预览列表' },
  { id: 4, title: 'ProviderMatrix', owner: '周同学', status: 'offline', priority: 'P3', scene: '前端展示', updatedAt: '2026-06-14', desc: 'Provider 对照矩阵' }
]

const tableColumns = [
  { key: 'title', label: '组件名', width: 24 },
  { key: 'scene', label: '场景', width: 16 },
  { key: 'owner', label: '负责人', width: 16 },
  { key: 'updatedAt', label: '更新时间', width: 18 },
  { key: 'status', label: '状态', width: 16, selectType: 'static', select_list: [{ value: 'online', label: '已上线' }, { value: 'draft', label: '草稿' }, { value: 'offline', label: '已下线' }] },
  { key: 'priority', label: '优先级', width: 12 }
]

const listConfig = {
  striped: true,
  actionColumns: [
    { key: 'preview', label: '预览' },
    { key: 'edit', label: '编辑' }
  ],
  sorts_config: {
    header_enable: true,
    sorts: ['updatedAt', 'priority']
  }
}

const summaryItems = computed(() => {
  const items = []
  filterSchema.value.forEach((field) => {
    if (field.value && field.key !== 'toggleAdvanced') {
      items.push({ key: field.label, value: field.value })
    }
  })
  advancedSchema.value.forEach((field) => {
    if (field.value) {
      items.push({ key: field.label, value: field.value })
    }
  })
  if (items.length) {
    items.push({ key: '清空全部', value: '', ghost: true, action: 'clear' })
  }
  return items
})

function handleFilterAction(payload) {
  if (payload?.action === 'toggle-advanced') {
    advancedVisible.value = !advancedVisible.value
  }
}

function handleSummaryAction(action) {
  if (action !== 'clear') return
  filterSchema.value.forEach((field) => {
    if (field.type !== 'button') field.value = ''
  })
  advancedSchema.value.forEach((field) => {
    field.value = ''
  })
}
</script>

<template>
  <div class="component-showcase">
    <div class="content-head">
      <div>
        <p class="eyebrow">常用组件</p>
        <h2>List 组件迁移预览</h2>
        <p class="section-desc">已将 `tradingview-vue/src/components/commons/list` 迁到当前前端，并适配为 Vue 3 可用版本。</p>
      </div>
    </div>

    <div class="preview-shell">
      <ListCommonLayout :sidebar-collapsed="sidebarCollapsed">
        <template #sidebar>
          <Sidebar title="组件分组" collapsible :collapsed="sidebarCollapsed" @toggle="sidebarCollapsed = $event">
            <template #main>
              <button
                v-for="item in sidebarItems"
                :key="item.key"
                type="button"
                class="sidebar-entry"
                :class="{ active: activeSection === item.key }"
                @click="activeSection = item.key"
              >
                <span>{{ item.label }}</span>
                <strong>{{ item.count }}</strong>
              </button>
            </template>
          </Sidebar>
        </template>

        <template #header>
          <HeaderBar title="组件资产" meta="迁移自 tradingview list" :show-title="true">
            <template #left>
              <FilterBar :schema="filterSchema" @action="handleFilterAction" />
            </template>
            <template #right>
              <ActionBar :actions="actionItems" />
            </template>
          </HeaderBar>
        </template>

        <template #advanced-filters>
          <AdvancedFilters :visible="advancedVisible" :schema="advancedSchema" />
        </template>

        <template #filter-summary>
          <FilterSummary :items="summaryItems" @action="handleSummaryAction" />
        </template>

        <template #stats>
          <StatsBar :items="statsItems" />
        </template>

        <template #table>
          <DataTable
            :rows="rows"
            :columns="tableColumns"
            :list-config="listConfig"
            :sorts="sorts"
            pagination
            :total-items="rows.length"
            :page="page"
            :page-size="pageSize"
            @update:sorts="sorts = $event"
            @update:page="page = $event"
            @update:pageSize="pageSize = $event"
          />
        </template>

        <template #footer>
          <DataListFooter
            :total-items="rows.length"
            :page="page"
            :page-size="pageSize"
            show-page-size
            @update:page="page = $event"
            @update:pageSize="pageSize = $event"
          />
        </template>
      </ListCommonLayout>
    </div>
  </div>
</template>

<style scoped>
.component-showcase {
  display: grid;
  gap: 18px;
  min-height: 0;
}

.preview-shell {
  height: min(760px, calc(100vh - 260px));
  min-height: 620px;
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
}

.sidebar-entry {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--stroke);
  background: var(--control-bg);
  color: var(--text);
}

.sidebar-entry.active {
  border-color: rgba(37, 99, 235, 0.35);
  box-shadow: 0 10px 18px rgba(37, 99, 235, 0.08);
}
</style>
