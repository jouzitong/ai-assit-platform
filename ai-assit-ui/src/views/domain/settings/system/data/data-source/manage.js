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
  },
  {
    key: 'ads_finance_pg',
    name: '财务分析 ADS',
    type: 'PostgreSQL',
    owner: '财务数据组',
    status: 'warning',
    statusLabel: '待校验',
    host: 'pg.ads.internal:5432',
    database: 'finance_ads',
    tables: 14,
    syncMode: 'T+1',
    updatedAt: '2026-06-09 21:45',
    summary: '面向财务看板和成本分析的汇总层数据源。'
  },
  {
    key: 'user_profile_clickhouse',
    name: '用户画像明细',
    type: 'ClickHouse',
    owner: '增长中台',
    status: 'online',
    statusLabel: '运行中',
    host: 'ck.user.internal:8123',
    database: 'user_profile',
    tables: 36,
    syncMode: '分钟级',
    updatedAt: '2026-06-10 09:58',
    summary: '存放用户行为、标签和画像宽表。'
  },
  {
    key: 'risk_sandbox_mysql',
    name: '风控沙箱库',
    type: 'MySQL',
    owner: '风险策略',
    status: 'offline',
    statusLabel: '已停用',
    host: 'mysql.risk.sandbox:3306',
    database: 'risk_sandbox',
    tables: 9,
    syncMode: '手动',
    updatedAt: '2026-06-07 18:12',
    summary: '策略验证与灰度实验使用的隔离环境。'
  }
]

export const tableCatalog = {
  ods_trade_mysql: [
    { name: 'ods_order_snapshot', columns: 42, rows: '1.2亿', partition: 'dt', freshness: '2 min', status: 'ready', statusLabel: '可用' },
    { name: 'ods_trade_fill', columns: 31, rows: '8600万', partition: 'dt', freshness: '30 sec', status: 'ready', statusLabel: '可用' },
    { name: 'ods_account_balance', columns: 18, rows: '120万', partition: 'none', freshness: '5 min', status: 'draft', statusLabel: '待配置' },
    { name: 'ods_order_payment', columns: 26, rows: '980万', partition: 'dt', freshness: '1 min', status: 'ready', statusLabel: '可用' }
  ],
  ads_finance_pg: [
    { name: 'ads_daily_income', columns: 24, rows: '6.3万', partition: 'biz_date', freshness: 'T+1', status: 'ready', statusLabel: '可用' },
    { name: 'ads_cost_center_overview', columns: 17, rows: '4200', partition: 'biz_date', freshness: 'T+1', status: 'ready', statusLabel: '可用' },
    { name: 'ads_cashflow_warning', columns: 12, rows: '900', partition: 'biz_date', freshness: 'T+1', status: 'warning', statusLabel: '待校验' }
  ],
  user_profile_clickhouse: [
    { name: 'dwd_user_event', columns: 56, rows: '28亿', partition: 'event_date', freshness: '1 min', status: 'ready', statusLabel: '可用' },
    { name: 'ads_user_tag_profile', columns: 84, rows: '3200万', partition: 'stat_date', freshness: '10 min', status: 'ready', statusLabel: '可用' },
    { name: 'ads_user_ltv_score', columns: 19, rows: '110万', partition: 'stat_date', freshness: '30 min', status: 'draft', statusLabel: '待配置' }
  ],
  risk_sandbox_mysql: [
    { name: 'sandbox_rule_case', columns: 15, rows: '2.4万', partition: 'none', freshness: 'manual', status: 'offline', statusLabel: '已停用' },
    { name: 'sandbox_hit_log', columns: 21, rows: '87万', partition: 'dt', freshness: 'manual', status: 'offline', statusLabel: '已停用' }
  ]
}

export const fieldCatalog = {
  ods_order_snapshot: [
    { name: 'snapshot_id', type: 'bigint', indexName: 'PRIMARY', relatedTable: '', description: '快照主键', statusLabel: '主键' },
    { name: 'order_id', type: 'varchar(64)', indexName: 'idx_order_id', relatedTable: 'ods_order_payment.order_id', description: '订单编号', statusLabel: '业务字段' },
    { name: 'user_id', type: 'bigint', indexName: 'idx_user_id', relatedTable: 'user_profile.user_id', description: '用户编号', statusLabel: '业务字段' },
    { name: 'order_status', type: 'varchar(32)', indexName: '', relatedTable: '', description: '订单状态', statusLabel: '业务字段' },
    { name: 'snapshot_time', type: 'datetime', indexName: 'idx_snapshot_time', relatedTable: '', description: '快照时间', statusLabel: '时间字段' }
  ],
  ods_trade_fill: [
    { name: 'fill_id', type: 'bigint', indexName: 'PRIMARY', relatedTable: '', description: '成交明细主键', statusLabel: '主键' },
    { name: 'trade_id', type: 'varchar(64)', indexName: 'idx_trade_id', relatedTable: 'ods_order_snapshot.order_id', description: '交易编号', statusLabel: '业务字段' },
    { name: 'symbol', type: 'varchar(32)', indexName: 'idx_symbol', relatedTable: '', description: '交易标的', statusLabel: '业务字段' },
    { name: 'fill_price', type: 'decimal(18,8)', indexName: '', relatedTable: '', description: '成交价格', statusLabel: '业务字段' },
    { name: 'fill_time', type: 'datetime', indexName: 'idx_fill_time', relatedTable: '', description: '成交时间', statusLabel: '时间字段' }
  ],
  ods_account_balance: [
    { name: 'account_id', type: 'bigint', indexName: 'PRIMARY', relatedTable: '', description: '账户编号', statusLabel: '主键' },
    { name: 'asset_code', type: 'varchar(32)', indexName: 'idx_asset_code', relatedTable: 'asset_dictionary.asset_code', description: '资产编码', statusLabel: '业务字段' },
    { name: 'available_balance', type: 'decimal(20,8)', indexName: '', relatedTable: '', description: '可用余额', statusLabel: '业务字段' },
    { name: 'frozen_balance', type: 'decimal(20,8)', indexName: '', relatedTable: '', description: '冻结余额', statusLabel: '业务字段' },
    { name: 'sync_time', type: 'datetime', indexName: 'idx_sync_time', relatedTable: '', description: '同步时间', statusLabel: '时间字段' }
  ],
  ods_order_payment: [
    { name: 'payment_id', type: 'bigint', indexName: 'PRIMARY', relatedTable: '', description: '支付主键', statusLabel: '主键' },
    { name: 'order_id', type: 'varchar(64)', indexName: 'idx_order_id', relatedTable: 'ods_order_snapshot.order_id', description: '订单编号', statusLabel: '业务字段' },
    { name: 'pay_method', type: 'varchar(32)', indexName: 'idx_pay_method', relatedTable: '', description: '支付方式', statusLabel: '业务字段' },
    { name: 'pay_amount', type: 'decimal(20,8)', indexName: '', relatedTable: '', description: '支付金额', statusLabel: '业务字段' },
    { name: 'pay_time', type: 'datetime', indexName: 'idx_pay_time', relatedTable: '', description: '支付时间', statusLabel: '时间字段' }
  ]
}

export const pageSizeOptions = [2, 4, 8]
