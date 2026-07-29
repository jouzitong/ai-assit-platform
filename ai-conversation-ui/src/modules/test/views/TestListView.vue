<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ListMainLayout from '../../../application/renderers/list/ListMainLayout.vue'
import type {
  ListRendererSchema,
  RendererAction,
  RendererQueryState,
  RendererTreeNode,
} from '../../../application/renderers/list/types'

type DemoRecord = {
  id: string
  status: number
  owner: string
  is_delete: number
  title: string
  tag: {
    title: string
  }
  categoryKey: string
  groupKey: string
  deadline: string
  priority: string
}

const baseSchema: ListRendererSchema = {
  id: 'qwerty12345678aeswrtyui3234',
  version: '1.0.0',
  title: '任务中心',
  component: 'zg-common-tree-list',
  tree: {
    component: 'group-list',
    title: '任务组列表',
  },
  tab: {
    activeTab: 'all',
    tabs: [
      { key: 'all', label: '全部' },
      { key: 'mine', label: '我的' },
    ],
  },
  datasource: {
    key: 'ai_kb_document',
    type: 'db-query-list',
    model: 'ai_kb_document',
    filter_dict: {
      status: 1,
      is_delete: 0,
    },
    filterExpr: 'status and is_delete',
    ext: {
      fields: ['id', 'title', 'owner', 'deadline', 'priority', 'tag.title'],
      relations: [
        {
          key: 'tag',
          model: 'ai_kb_document_tag',
          type: 'left',
          on: {
            id: 'document_id',
          },
          filter: {
            status: 1,
            is_delete: 0,
          },
        },
      ],
    },
  },
  filters: [
    {
      key: 'title',
      label: '标题',
      component: 'zg-input',
    },
    {
      key: 'owner',
      label: '负责人',
      component: 'zg-selector',
      list: [
        { key: 'zhou', value: 'zhou' },
        { key: 'amy', value: 'amy' },
      ],
      options: {
        multiple: true,
        filterable: true,
        clearable: true,
        styles: {
          width: '240px',
        },
      },
    },
  ],
  fields: [
    {
      key: 'id',
      name: 'id',
      label: 'ID',
      field: ['id'],
      options: {
        styles: {
          'text-align': 'center',
          width: 8,
        },
        className: 'text-center',
      },
    },
    {
      key: 'title',
      name: 'title',
      label: '任务',
      field: ['tag', 'title'],
      options: {
        styles: {
          'text-align': 'left',
          width: 20,
        },
        className: 'text-left',
      },
    },
    {
      key: 'owner',
      name: 'owner',
      label: '负责人',
      field: ['owner'],
      options: {
        styles: {
          'text-align': 'left',
          width: 18,
        },
      },
    },
    {
      key: 'deadline',
      name: 'deadline',
      label: '截止时间',
      field: ['deadline'],
      options: {
        styles: {
          'text-align': 'left',
          width: 18,
        },
      },
    },
    {
      key: 'priority',
      name: 'priority',
      label: '优先级',
      field: ['priority'],
      options: {
        styles: {
          'text-align': 'left',
          width: 14,
        },
      },
    },
  ],
  actions: [
    {
      key: 'edit-page',
      name: '编辑页',
      action: 'EDIT_PAGE',
    },
    {
      key: 'create',
      name: '新增',
      action: 'ADD',
      options: { type: 'primary' },
    },
    {
      key: 'sync',
      name: '同步',
      action: 'SYNC',
    },
    {
      key: 'main',
      name: '主按钮',
      action: 'PRIMARY_ACTION',
      options: { type: 'success' },
    },
    {
      key: 'more',
      name: '更多操作',
      action: 'MORE',
    },
  ],
  summary: {
    cards: [
      { key: 'pending', label: '待处理', value: 18, accent: '#f7c85b' },
      { key: 'running', label: '进行中', value: 27, accent: '#9b8cff' },
      { key: 'review', label: '待评审', value: 9, accent: '#8ea3ff' },
      { key: 'done', label: '本周完成', value: 22, accent: '#5eead4' },
      { key: 'overdue', label: '超期', value: 4, accent: '#fb7185' },
      { key: 'expiring', label: '即将到期', value: 11, accent: '#fbbf24' },
    ],
  },
  list_config: {
    variant: 'workbench',
    itemType: 'table',
    actionColumns: [
      {
        key: 'view',
        name: '查看',
        action: 'VIEW',
        options: { type: 'primary' },
      },
    ],
    pagination: {
      enabled: true,
      pageSize: 5,
      pageSizeOptions: [5, 10, 20, 30],
    },
    className: 'card-list',
    events: [],
  },
  hooks: {
    beforeLoad: '=function(){}',
    afterLoad: '=function(){}',
  },
}

const schemaState = ref<ListRendererSchema>(cloneSchema(baseSchema))
const metadataDialogVisible = ref(false)
const metadataDraft = ref(stringifySchema(schemaState.value))

const schemaWithMetadataAction = computed<ListRendererSchema>(() => ({
  ...schemaState.value,
  actions: [
    ...(schemaState.value.actions || []),
    {
      key: '__metadata__',
      name: '元数据',
      action: 'METADATA',
      options: { type: 'info' },
    },
  ],
}))

const treeData: RendererTreeNode[] = [
  {
    key: 'growth',
    label: '增长实验',
    children: [
      { key: 'ab', label: 'AB 实验', count: 14 },
      { key: 'funnel', label: '转化漏斗', count: 6 },
      { key: 'campaign', label: '活动运营', count: 9 },
    ],
  },
  {
    key: 'product',
    label: '产品迭代',
    children: [
      { key: 'ux', label: '交互体验', count: 7 },
      { key: 'trade', label: '交易性能', count: 12 },
      { key: 'risk', label: '风控策略', count: 5 },
      { key: 'board', label: '数据看板', count: 8 },
    ],
  },
  {
    key: 'infra',
    label: '基础建设',
    children: [
      { key: 'stability', label: '稳定性提升', count: 4 },
      { key: 'monitor', label: '监控告警', count: 3 },
      { key: 'cost', label: '成本优化', count: 5 },
    ],
  },
]

const records = reactive<DemoRecord[]>([
  {
    id: 'DOC-1001',
    status: 1,
    owner: 'zhou',
    is_delete: 0,
    title: '员工入职手册',
    tag: { title: '员工入职手册' },
    categoryKey: 'policy',
    groupKey: 'stability',
    deadline: '2026/02/05',
    priority: 'P1',
  },
  {
    id: 'DOC-1002',
    status: 1,
    owner: 'zhou',
    is_delete: 0,
    title: '知识库接入说明',
    tag: { title: '知识库接入说明' },
    categoryKey: 'product',
    groupKey: 'board',
    deadline: '2026/02/06',
    priority: 'P2',
  },
  {
    id: 'DOC-1003',
    status: 1,
    owner: 'amy',
    is_delete: 0,
    title: '模型配置发布流程',
    tag: { title: '模型配置发布流程' },
    categoryKey: 'ops',
    groupKey: 'risk',
    deadline: '2026/02/04',
    priority: 'P1',
  },
  {
    id: 'DOC-1004',
    status: 1,
    owner: 'zhou',
    is_delete: 0,
    title: '向量检索参数建议',
    tag: { title: '向量检索参数建议' },
    categoryKey: 'product',
    groupKey: 'ux',
    deadline: '2026/02/07',
    priority: 'P2',
  },
  {
    id: 'DOC-1005',
    status: 1,
    owner: 'amy',
    is_delete: 0,
    title: '权限审批流程',
    tag: { title: '权限审批流程' },
    categoryKey: 'policy',
    groupKey: 'monitor',
    deadline: '2026/02/08',
    priority: 'P0',
  },
  {
    id: 'DOC-1006',
    status: 1,
    owner: 'zhou',
    is_delete: 0,
    title: '渲染模块发布清单',
    tag: { title: '渲染模块发布清单' },
    categoryKey: 'ops',
    groupKey: 'cost',
    deadline: '2026/02/06',
    priority: 'P1',
  },
])

const queryState = reactive<RendererQueryState>({
  activeTab: 'all',
  filters: {
    title: '',
    owner: [],
  },
  page: 1,
  pageSize: 5,
  selectedTreeKey: null,
})

const filteredRecords = computed(() => {
  const keyword = String(queryState.filters.title ?? '').trim().toLowerCase()

  return records.filter((item) => {
    const selectedOwners = Array.isArray(queryState.filters.owner) ? queryState.filters.owner : []
    const matchTab = queryState.activeTab === 'all' || item.owner === 'zhou'
    const matchKeyword = !keyword || item.title.toLowerCase().includes(keyword) || item.id.toLowerCase().includes(keyword)
    const matchTree = !queryState.selectedTreeKey || item.groupKey === queryState.selectedTreeKey
    const matchOwner = selectedOwners.length === 0 || selectedOwners.includes(item.owner)
    return matchTab && matchKeyword && matchTree && matchOwner
  })
})

const pagedRecords = computed(() => {
  const start = (queryState.page - 1) * queryState.pageSize
  const end = start + queryState.pageSize
  return filteredRecords.value.slice(start, end)
})

function handleQueryChange(nextQuery: RendererQueryState) {
  Object.assign(queryState, nextQuery)
}

function handleReload(nextQuery: RendererQueryState) {
  Object.assign(queryState, nextQuery)
  ElMessage.info(`reload: tab=${nextQuery.activeTab}, page=${nextQuery.page}, size=${nextQuery.pageSize}`)
}

function handleAction(action: RendererAction) {
  if (action.action === 'METADATA') {
    metadataDraft.value = stringifySchema(schemaState.value)
    metadataDialogVisible.value = true
    return
  }
  ElMessage.success(`header action: ${action.action}`)
}

function handleItemAction(payload: { action: RendererAction; row: Record<string, unknown> }) {
  ElMessage.warning(`row action: ${payload.action.action} / ${String(payload.row.id ?? '')}`)
}

function handleFormatMetadata() {
  try {
    metadataDraft.value = stringifySchema(JSON.parse(metadataDraft.value) as ListRendererSchema)
  } catch {
    ElMessage.error('当前 JSON 格式不合法，无法格式化')
  }
}

function handleResetMetadata() {
  schemaState.value = cloneSchema(baseSchema)
  metadataDraft.value = stringifySchema(schemaState.value)
  metadataDialogVisible.value = false
  ElMessage.success('已恢复默认元数据')
}

function handleApplyMetadata() {
  try {
    const nextSchema = JSON.parse(metadataDraft.value) as ListRendererSchema
    schemaState.value = cloneSchema(nextSchema)
    metadataDraft.value = stringifySchema(schemaState.value)
    metadataDialogVisible.value = false
    ElMessage.success('元数据已应用，列表已重新渲染')
  } catch (error) {
    ElMessage.error(error instanceof Error ? `JSON 解析失败: ${error.message}` : 'JSON 解析失败')
  }
}

function cloneSchema(schema: ListRendererSchema) {
  return JSON.parse(JSON.stringify(schema)) as ListRendererSchema
}

function stringifySchema(schema: ListRendererSchema) {
  return JSON.stringify(schema, null, 2)
}
</script>

<template>
  <section class="test-list-view">
    <ListMainLayout
      :schema="schemaWithMetadataAction"
      :records="pagedRecords"
      :tree-data="treeData"
      :total="filteredRecords.length"
      @query-change="handleQueryChange"
      @reload="handleReload"
      @action="handleAction"
      @item-action="handleItemAction"
    />

    <el-dialog
      v-model="metadataDialogVisible"
      title="页面元数据"
      width="960px"
      destroy-on-close
      class="test-list-view__metadata-dialog"
    >
      <div class="test-list-view__metadata-toolbar">
        <el-button @click="handleFormatMetadata">格式化 JSON</el-button>
        <el-button @click="handleResetMetadata">恢复默认</el-button>
      </div>

      <el-input
        v-model="metadataDraft"
        type="textarea"
        :rows="24"
        class="test-list-view__metadata-input"
        spellcheck="false"
      />

      <template #footer>
        <div class="test-list-view__metadata-footer">
          <el-button @click="metadataDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleApplyMetadata">应用并重渲染</el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.test-list-view {
  min-height: 100%;
}

.test-list-view__metadata-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.test-list-view__metadata-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

:deep(.test-list-view__metadata-input textarea) {
  min-height: 520px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  line-height: 1.55;
}

@media (max-width: 960px) {
  .test-list-view__metadata-toolbar {
    flex-direction: column;
  }
}
</style>
