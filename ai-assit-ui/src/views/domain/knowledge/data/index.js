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
  }
]

export const KNOWLEDGE_TABLE_COLUMNS = [
  { key: 'kbCode', label: '知识库编码', width: 12 },
  { key: 'documentCode', label: '文档编码', width: 16 },
  { key: 'documentName', label: '文档名称', width: 16 },
  { key: 'documentType', label: '文档类型', width: 12 },
  { key: 'bizType', label: '业务类型', width: 12 },
  { key: 'bizKey', label: '业务唯一键', width: 14 },
  { key: 'sourceSystem', label: '来源系统', width: 12 },
  {
    key: 'status',
    label: '状态',
    width: 10,
    class: row => `knowledge-status ${row.statusClass || ''}`
  },
  { key: 'draftVersionNo', label: '草稿版本号', width: 10 },
  { key: 'contentFormat', label: '内容格式', width: 12 },
  { key: 'contentSize', label: '内容大小', width: 10 },
  { key: 'reviewStatus', label: '审核状态', width: 12 },
  { key: 'lastGeneratedAt', label: '最近生成时间', width: 14 }
]

export const KNOWLEDGE_LIST_CONFIG = {
  striped: true,
  actionColumns: [
    { key: 'preview', label: '查看正文' }
  ],
  sorts_config: {
    header_enable: true,
    sorts: ['kbCode', 'documentCode', 'documentName', 'documentType', 'bizType', 'status', 'draftVersionNo', 'reviewStatus', 'lastGeneratedAt']
  }
}

export const KNOWLEDGE_ACTION_ITEMS = [
  { key: 'create', label: '新建知识库', type: 'primary', action: 'create' },
  { key: 'refresh', label: '刷新', variant: 'ghost', action: 'refresh' },
  { key: 'sync', label: '同步', variant: 'ghost', action: 'sync' }
]

export const KNOWLEDGE_HEADER_TABS = [
  { key: 'current', label: '生效' },
  { key: 'draft', label: '草稿' },
  { key: 'history', label: '历史' }
]

export { KNOWLEDGE_DOCUMENTS, getKnowledgeDocumentByCode } from './documents'
