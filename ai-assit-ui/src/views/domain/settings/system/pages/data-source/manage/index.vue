<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Download, Plus, RefreshRight, Upload } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const dataSources = [
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

const tableCatalog = {
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

const fieldCatalog = {
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

const sourceKey = computed(() => String(route.params.sourceKey ?? ''))
const currentSource = computed(() => {
  return dataSources.find(item => item.key === sourceKey.value) ?? dataSources[0]
})

const currentTables = computed(() => tableCatalog[currentSource.value.key] ?? [])
const fieldWorkbenchVisible = ref(false)
const selectedTableName = ref(currentTables.value[0]?.name ?? '')
const pageSizeOptions = [2, 4, 8]
const pagination = reactive({
  page: 1,
  size: 4
})

const totalPages = computed(() => Math.max(1, Math.ceil(currentTables.value.length / pagination.size)))
const pagedTables = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  return currentTables.value.slice(start, start + pagination.size)
})
const pageSummary = computed(() => `共 ${currentTables.value.length} 条，${pagination.page} / ${totalPages.value} 页`)
const selectedTable = computed(() => {
  return currentTables.value.find(item => item.name === selectedTableName.value) ?? currentTables.value[0] ?? null
})
const selectedFields = computed(() => fieldCatalog[selectedTable.value?.name ?? ''] ?? [])

watch(currentSource, () => {
  pagination.page = 1
  fieldWorkbenchVisible.value = false
  selectedTableName.value = currentTables.value[0]?.name ?? ''
})

function statusClass(status) {
  return `is-${status}`
}

function handlePageChange(page) {
  pagination.page = Math.min(Math.max(page, 1), totalPages.value)
}

function handlePageSizeChange(event) {
  pagination.size = Number(event.target.value)
  pagination.page = 1
}

function handleSourceChange(event) {
  router.push(`/settings/system/data-source/${event.target.value}`)
}

function openFieldWorkbench(item) {
  selectedTableName.value = item.name
  fieldWorkbenchVisible.value = true
}

function selectTable(item) {
  selectedTableName.value = item.name
}

function formatEmpty(value) {
  return value?.trim ? (value.trim() || '无') : (value || '无')
}

function goBack() {
  router.push('/settings/system/data-source')
}
</script>

<template>
  <div class="data-source-manage-page">
    <section class="content-head compact">
      <div class="head-copy">
        <button type="button" class="back-btn" @click="goBack">
          <ArrowLeft :size="16" />
          返回列表
        </button>
        <div>
          <p class="eyebrow">数据表管理</p>
          <select class="source-switcher" :value="currentSource.key" aria-label="切换数据源" @change="handleSourceChange">
            <option v-for="source in dataSources" :key="source.key" :value="source.key">
              {{ source.name }} · {{ source.type }}
            </option>
          </select>
          <p class="section-desc">
            这里主要管理该数据源下的表配置、字段、同步和权限。
          </p>
        </div>
      </div>

      <div class="head-actions">
        <button type="button" class="toolbar-btn secondary">
          <RefreshRight :size="16" />
          刷新
        </button>
        <button type="button" class="toolbar-btn secondary">
          <Upload :size="16" />
          导入
        </button>
        <button type="button" class="toolbar-btn secondary">
          <Download :size="16" />
          导出
        </button>
        <button type="button" class="toolbar-btn primary">
          <Plus :size="16" />
          新增表
        </button>
      </div>
    </section>

    <section v-if="!fieldWorkbenchVisible" class="table-card">
      <div class="table-body">
        <table class="config-table">
          <thead>
            <tr>
              <th>表名</th>
              <th>字段数</th>
              <th>数据量</th>
              <th>分区键</th>
              <th>新鲜度</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in pagedTables" :key="item.name">
              <td><strong>{{ item.name }}</strong></td>
              <td>{{ item.columns }}</td>
              <td>{{ item.rows }}</td>
              <td>{{ item.partition }}</td>
              <td>{{ item.freshness }}</td>
              <td><span class="status-chip" :class="statusClass(item.status)">{{ item.statusLabel }}</span></td>
              <td class="row-actions">
                <button type="button" class="link-btn">数据查看</button>
                <button type="button" class="link-btn" @click="openFieldWorkbench(item)">字段</button>
                <button type="button" class="link-btn">同步</button>
                <button type="button" class="link-btn">权限</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer class="pagination-bar">
        <span class="page-summary">{{ pageSummary }}</span>
        <div class="page-controls">
          <select class="field-control page-size" :value="pagination.size" @change="handlePageSizeChange">
            <option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }} / 页</option>
          </select>

          <div class="pager">
            <button class="action-btn" type="button" :disabled="pagination.page <= 1" @click="handlePageChange(pagination.page - 1)">
              上一页
            </button>
            <span class="pager-indicator">{{ pagination.page }} / {{ totalPages }}</span>
            <button
              class="action-btn"
              type="button"
              :disabled="pagination.page >= totalPages"
              @click="handlePageChange(pagination.page + 1)"
            >
              下一页
            </button>
          </div>
        </div>
      </footer>
    </section>

    <section v-else class="field-workbench">
      <aside class="table-picker">
        <div class="picker-head">
          <p class="eyebrow">表切换</p>
          <h3>表名称</h3>
        </div>
        <button
          v-for="item in currentTables"
          :key="item.name"
          type="button"
          class="table-item"
          :class="{ active: selectedTableName === item.name }"
          @click="selectTable(item)"
        >
          <strong>{{ item.name }}</strong>
          <span>{{ item.columns }} 字段 · {{ item.statusLabel }}</span>
        </button>
      </aside>

      <section class="field-panel">
        <div class="picker-head">
          <p class="eyebrow">字段列表</p>
          <h3>{{ selectedTable?.name }}</h3>
        </div>

        <div class="field-meta">
          <span>数据量 {{ selectedTable?.rows }}</span>
          <span>分区键 {{ selectedTable?.partition }}</span>
          <span>新鲜度 {{ selectedTable?.freshness }}</span>
        </div>

        <table class="field-table">
          <thead>
            <tr>
              <th>字段名</th>
              <th>类型</th>
              <th>索引</th>
              <th>外键关联表</th>
              <th>说明</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="field in selectedFields" :key="field.name">
              <td><strong>{{ field.name }}</strong></td>
              <td>{{ field.type }}</td>
              <td>{{ formatEmpty(field.indexName) }}</td>
              <td>{{ formatEmpty(field.relatedTable) }}</td>
              <td>{{ field.description }}</td>
              <td><span class="status-chip is-ready">{{ field.statusLabel }}</span></td>
            </tr>
          </tbody>
        </table>
      </section>
    </section>
  </div>
</template>

<style scoped>
.data-source-manage-page {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 16px;
  min-height: calc(100% + 18px);
  margin-bottom: -18px;
}

.head-copy {
  display: grid;
  gap: 12px;
}

.source-switcher {
  width: fit-content;
  min-width: 0;
  max-width: 100%;
  border: 0;
  padding: 0;
  background: transparent;
  color: #0f172a;
  font-size: 26px;
  line-height: 1.15;
  font-weight: 800;
  letter-spacing: -0.02em;
  appearance: none;
  cursor: pointer;
}

.back-btn,
.toolbar-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 0;
  border-radius: 14px;
  cursor: pointer;
  font: inherit;
}

.back-btn {
  display: inline-flex;
  flex-direction: row;
  width: auto;
  min-width: 0;
  height: 44px;
  padding: 0 14px;
  color: #0f172a;
  background: rgba(226, 232, 240, 0.45);
  white-space: nowrap;
  flex-shrink: 0;
  line-height: 1;
  writing-mode: horizontal-tb;
  text-orientation: mixed;
}

.back-btn :deep(svg) {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
}

.head-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.toolbar-btn {
  height: 44px;
  padding: 0 14px;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 600;
}

.toolbar-btn.primary {
  color: #eff6ff;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.16);
}

.toolbar-btn.secondary {
  color: #0f172a;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.95);
}

.table-card {
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 22px;
  background: #fff;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 16px;
  padding: 18px;
}

.field-workbench {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  gap: 16px;
  min-height: 0;
}

.table-picker,
.field-panel {
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 22px;
  background: #fff;
  padding: 18px;
  min-height: 0;
}

.table-picker {
  display: grid;
  gap: 12px;
  align-content: start;
  overflow: auto;
}

.picker-head {
  display: grid;
  gap: 6px;
}

.picker-head h3 {
  margin: 0;
  font-size: 18px;
  line-height: 1.2;
  color: #0f172a;
}

.table-item {
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.88);
  padding: 12px 14px;
  display: grid;
  gap: 4px;
  text-align: left;
  cursor: pointer;
}

.table-item strong {
  font-size: 14px;
  color: #0f172a;
}

.table-item span,
.field-meta {
  color: #64748b;
  font-size: 13px;
}

.table-item.active {
  border-color: rgba(37, 99, 235, 0.45);
  background: rgba(37, 99, 235, 0.08);
}

.field-panel {
  display: grid;
  gap: 14px;
  overflow: auto;
}

.field-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
}

.field-table {
  width: 100%;
  border-collapse: collapse;
}

.field-table th,
.field-table td {
  padding: 12px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.95);
  text-align: left;
  vertical-align: top;
}

.field-table th {
  color: #64748b;
  font-size: 13px;
}

.field-control {
  width: 100%;
  min-width: 0;
  height: 42px;
  border: 1px solid rgba(203, 213, 225, 0.96);
  border-radius: 14px;
  padding: 0 14px;
  background: rgba(255, 255, 255, 0.96);
  font-size: 14px;
  color: #0f172a;
  box-sizing: border-box;
}

select.field-control {
  appearance: none;
}

.table-card-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
}

.table-body {
  min-height: 0;
  overflow: auto;
}

.config-table {
  width: 100%;
  border-collapse: collapse;
}

.config-table th,
.config-table td {
  padding: 12px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.95);
  text-align: left;
  vertical-align: top;
}

.config-table th {
  color: #64748b;
  font-size: 13px;
}

.row-actions {
  white-space: nowrap;
}

.link-btn {
  border: 0;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  padding: 0;
  font: inherit;
  font-size: 12px;
}

.link-btn + .link-btn {
  margin-left: 12px;
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 4px 0;
}

.page-summary {
  font-size: 13px;
  color: #64748b;
}

.page-controls {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.page-size {
  width: 108px;
}

.pager {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.pager-indicator {
  font-size: 13px;
  color: #475569;
  font-weight: 700;
}

.action-btn {
  height: 40px;
  border: 0;
  border-radius: 14px;
  padding: 0 16px;
  background: rgba(241, 245, 249, 0.96);
  color: #0f172a;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.action-btn.primary {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #fff;
}

.action-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.eyebrow {
  margin: 0 0 8px;
  color: #2563eb;
  text-transform: uppercase;
  letter-spacing: 0.2em;
  font-size: 12px;
  font-weight: 700;
}

.section-desc {
  margin: 10px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.source-switcher:focus {
  outline: none;
}

.source-switcher option {
  font-size: 14px;
  font-weight: 500;
}

@media (max-width: 760px) {
  .table-card-head {
    align-items: stretch;
  }

  .toolbar-btn {
    width: 100%;
    justify-content: center;
  }

  .pagination-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .page-controls {
    width: 100%;
    justify-content: space-between;
    flex-wrap: wrap;
  }

  .pager {
    width: 100%;
    justify-content: space-between;
  }

  .field-workbench {
    grid-template-columns: 1fr;
  }
}
</style>
