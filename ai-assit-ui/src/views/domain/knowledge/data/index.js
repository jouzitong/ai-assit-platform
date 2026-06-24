export const KNOWLEDGE_PAGE_TITLE = '知识库 · V1.0.0'

export const KNOWLEDGE_FILTER_SCHEMA = [
  {
    key: 'keyword',
    label: '搜索',
    type: 'input',
    value: '',
    action: 'keyword-change',
    type_config: {
      placeholder: '搜索 kbCode / documentCode / documentName / bizKey',
      width: 280,
      clearable: true
    }
  },
  {
    key: 'bizTypeCode',
    label: '业务类型',
    type: 'select',
    value: '',
    action: 'biz-type-change',
    type_config: {
      width: 180,
      clearable: true,
      options: [{ code: '', name: '全部业务类型' }]
    }
  }
]

export const KNOWLEDGE_TABLE_COLUMNS = [
  { key: 'kbCode', label: '知识库编码', width: 12 },
  { key: 'documentCode', label: '文档编码', width: 18 },
  { key: 'documentName', label: '文档名称', width: 20 },
  { key: 'documentType', label: '文档类型', width: 12, selectType: 'enums', select_key: 'aiKbDocumentType' },
  { key: 'bizType', label: '业务类型', width: 12, selectType: 'enums', select_key: 'aiKbBizType' },
  { key: 'bizKey', label: '业务唯一键', width: 14 },
  { key: 'sourceSystem', label: '来源系统', width: 12 },
  {
    key: 'status',
    label: '状态',
    width: 10,
    alignCenter: true,
    selectType: 'enums',
    select_key: 'aiKbDocumentStatus',
    class: row => `knowledge-status ${row.statusClass || ''}`
  },
  { key: 'providerSyncStatus', label: '同步状态', width: 12, alignCenter: true, selectType: 'enums', select_key: 'aiKbProviderSyncStatus' },
  { key: 'providerDocumentId', label: '远端文档ID', width: 18 },
  { key: 'currentVersionNo', label: '当前版本号', width: 9, alignCenter: true },
  { key: 'contentFormat', label: '内容格式', width: 10, alignCenter: true, selectType: 'enums', select_key: 'aiKbContentFormat' },
  { key: 'contentSize', label: '内容大小', width: 9, alignCenter: true },
  { key: 'lastGeneratedAt', label: '最近生成时间', width: 14, alignCenter: true }
]

export const KNOWLEDGE_LIST_CONFIG = {
  striped: true,
  actionColumns: [
    { key: 'preview', label: '查看正文' }
  ],
  sorts_config: {
    header_enable: true,
    sorts: ['kbCode', 'documentCode', 'documentName', 'documentType', 'bizType', 'status', 'providerSyncStatus', 'currentVersionNo', 'lastGeneratedAt']
  }
}

export const KNOWLEDGE_ACTION_ITEMS = [
  { key: 'create', label: '新建知识库', type: 'primary', action: 'create' },
  { key: 'refresh', label: '刷新', variant: 'ghost', action: 'refresh' },
  { key: 'sync', label: '同步', variant: 'ghost', action: 'sync' }
]

export const KNOWLEDGE_HEADER_TABS = [
  { key: 'current', label: '当前文档' },
  { key: 'history', label: '历史版本' }
]

export { KNOWLEDGE_DOCUMENTS, getKnowledgeDocumentByCode } from './documents'
