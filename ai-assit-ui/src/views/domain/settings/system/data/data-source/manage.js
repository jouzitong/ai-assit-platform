export const dataSources = [
  {
    key: 'ods_trade_mysql',
    name: '交易核心 ODS',
    type: 'MySQL',
    owner: '交易平台',
    status: 'online',
    statusLabel: '运行中',
    host: 'mysql.trade.internal:3306',
    database: 'trade_ods',
    tables: 28,
    syncMode: '实时',
    updatedAt: '2026-06-10 10:20',
    summary: '负责承接订单、成交、资金流水等 ODS 数据。'
  }
]

export const tableCatalog = {}

export const fieldCatalog = {}

export const pageSizeOptions = [2, 4, 8]
