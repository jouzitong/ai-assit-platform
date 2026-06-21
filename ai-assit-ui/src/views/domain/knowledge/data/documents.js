export const KNOWLEDGE_DOCUMENTS = [
  {
    id: 1,
    kbCode: 'trade_core_kb',
    documentCode: 'trade_core_ods',
    documentName: '交易核心 ODS',
    documentType: 'DB_TABLE',
    bizType: 'DATABASE',
    bizKey: 'trade_core_ods',
    sourceSystem: 'db-engine',
    status: 'ACTIVE',
    providerDocumentId: 'trade_core_ods',
    providerSyncStatus: 'SUCCESS',
    currentVersionNo: 3,
    contentFormat: 'MARKDOWN',
    contentSize: 1280,
    lastGeneratedAt: '2026-06-17T10:00:00',
    remark: '交易核心知识库主文档',
    renderedContent: `# 交易核心 ODS

## 表说明
- 表名: trade_ods
- 主题域: 交易核心
- 数据来源: db-engine

## 核心字段
- order_id: 订单编号
- account_id: 账户编号
- instrument_id: 产品编号
- side: 买卖方向
- quantity: 下单数量
- price: 下单价格

## 使用建议
- 用于交易链路问题排查
- 用于订单状态分析与口径对齐
`,
    contentJson: {
      tableName: 'trade_ods',
      domain: 'trade',
      fields: ['order_id', 'account_id', 'instrument_id', 'side', 'quantity', 'price']
    },
    metaJson: {
      owner: '交易平台 / admin'
    },
    extJson: {
      sourceSystem: 'db-engine'
    }
  },
  {
    id: 2,
    kbCode: 'risk_review_kb',
    documentCode: 'risk_rule_draft',
    documentName: '风控规则草稿',
    documentType: 'MARKDOWN_DOC',
    bizType: 'FILE',
    bizKey: 'risk_rule_draft',
    sourceSystem: 'manual-import',
    status: 'ACTIVE',
    providerDocumentId: '',
    providerSyncStatus: 'PENDING',
    currentVersionNo: 1,
    contentFormat: 'MARKDOWN',
    contentSize: 860,
    lastGeneratedAt: '2026-06-16T15:30:00',
    remark: '待审核文档',
    renderedContent: `# 风控规则草稿

## 目标
统一账户级限额与告警规则。

## 当前草稿项
1. 单账户分钟级成交额阈值
2. 异常撤单频率阈值
3. 跨市场联动告警规则
`,
    contentJson: {},
    metaJson: {
      owner: '风控团队'
    },
    extJson: {
      sourceSystem: 'manual-import'
    }
  },
  {
    id: 3,
    kbCode: 'archive_kb',
    documentCode: 'legacy_settlement',
    documentName: '历史清算文档',
    documentType: 'TEXT_DOC',
    bizType: 'FILE',
    bizKey: 'legacy_settlement',
    sourceSystem: 'archive-center',
    status: 'DISABLED',
    providerDocumentId: 'legacy_settlement',
    providerSyncStatus: 'FAILED',
    currentVersionNo: 7,
    contentFormat: 'TEXT',
    contentSize: 540,
    lastGeneratedAt: '2026-05-20T09:15:00',
    remark: '历史归档内容，仅供追溯',
    renderedContent: `历史清算链路说明文档。

当前文档处于停用状态，仅用于历史回溯，不参与现网知识检索。`,
    contentJson: {},
    metaJson: {
      owner: '清算团队'
    },
    extJson: {
      sourceSystem: 'archive-center'
    }
  }
]

export function getKnowledgeDocumentByCode(kbCode, documentCode) {
  return KNOWLEDGE_DOCUMENTS.find(item =>
    String(item.kbCode) === String(kbCode) && String(item.documentCode) === String(documentCode)
  ) || null
}
