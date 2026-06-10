<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Connection, DataBoard, Plus, Search } from '@element-plus/icons-vue'

const router = useRouter()
const keyword = ref('')
const selectedSourceKey = ref('ods_trade_mysql')

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
    tags: ['生产', '主链路'],
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
    tags: ['分析', 'BI'],
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
    tags: ['画像', '行为'],
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
    tags: ['沙箱', '试验'],
    summary: '策略验证与灰度实验使用的隔离环境。'
  }
]

const filteredSources = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  if (!normalized) {
    return dataSources
  }

  return dataSources.filter(item =>
    [item.name, item.type, item.owner, item.host, item.database].some(value =>
      String(value).toLowerCase().includes(normalized)
    )
  )
})

function openSource(key) {
  selectedSourceKey.value = key
  router.push(`/settings/system/data-source/${key}`)
}

function statusClass(status) {
  return `is-${status}`
}
</script>

<template>
  <div class="data-source-page">
    <section class="content-head compact">
      <div>
        <p class="eyebrow">数据源配置</p>
        <h2>把连接管理和表数据运维收在一个工作台里</h2>
        <p class="section-desc">
          第一屏先看数据源清单，确认名称、连接信息、类型、状态和归属。
        </p>
      </div>
    </section>

    <section class="source-list-card">
      <div class="toolbar">
        <label class="search-box">
          <Search :size="16" />
          <input v-model="keyword" type="text" placeholder="搜索数据源名称、类型、负责人或库名" />
        </label>

        <div class="toolbar-actions">
          <button type="button" class="toolbar-add-btn">
            <Plus :size="16" />
            新增
          </button>
        </div>
      </div>

      <div class="source-stack">
        <div
          v-for="item in filteredSources"
          :key="item.key"
          class="source-row"
          :class="{ active: selectedSourceKey === item.key }"
          @click="openSource(item.key)"
          role="button"
          tabindex="0"
          @keydown.enter.prevent="openSource(item.key)"
          @keydown.space.prevent="openSource(item.key)"
        >
          <div class="source-row-main">
            <div class="source-title">
              <strong>{{ item.name }}</strong>
              <span class="status-chip" :class="statusClass(item.status)">{{ item.statusLabel }}</span>
            </div>

            <p>{{ item.summary }}</p>

            <div class="source-meta">
              <span><Connection :size="14" /> {{ item.host }}</span>
              <span><DataBoard :size="14" /> {{ item.database }}</span>
              <span>{{ item.type }}</span>
              <span>{{ item.owner }}</span>
            </div>
          </div>

          <div class="source-row-side">
            <div class="source-metric">
              <strong>{{ item.tables }}</strong>
              <span>表数量</span>
            </div>
            <div class="source-metric">
              <strong>{{ item.syncMode }}</strong>
              <span>同步频率</span>
            </div>
            <button type="button" class="row-action-btn" @click.stop>
              操作
            </button>
          </div>
        </div>

        <div v-if="!filteredSources.length" class="placeholder-panel">
          <p>没有匹配到数据源。这里后续可以接真实接口，支持按状态、类型和负责人筛选。</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.data-source-page {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 18px;
  min-height: calc(100% + 18px);
  margin-bottom: -18px;
}

.source-list-card {
  display: grid;
  min-height: 0;
  height: 100%;
  grid-template-rows: auto minmax(0, 1fr);
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 22px;
  background: #fff;
  padding: 18px;
  gap: 16px;
}

.source-stack {
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  align-content: start;
  padding-right: 4px;
  overflow: auto;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.toolbar-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex: none;
}

.toolbar-add-btn {
  height: 48px;
  padding: 0 16px;
  border: 0;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
  cursor: pointer;
  color: #eff6ff;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.16);
  font: inherit;
  font-size: 14px;
  font-weight: 600;
}

.toolbar-add-btn :deep(svg) {
  width: 16px;
  height: 16px;
  flex: none;
}

.search-box {
  width: 100%;
  max-width: 560px;
  min-height: 48px;
  padding: 0 14px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  box-sizing: border-box;
}

.search-box :deep(svg) {
  width: 16px;
  height: 16px;
  flex: none;
}

.search-box input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: #0f172a;
  font-size: 14px;
  line-height: 20px;
  padding: 0;
}

.search-box input::placeholder {
  color: #94a3b8;
}

.source-row {
  width: 100%;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 20px;
  padding: 12px 14px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.source-row:hover,
.source-row.active {
  transform: translateY(-1px);
  border-color: rgba(37, 99, 235, 0.22);
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.06);
}

.source-row-main,
.source-title,
.source-meta,
.source-row-side,
.source-metric {
  display: grid;
}

.source-title {
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.source-title strong {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
}

.source-row-main p {
  margin: 6px 0 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
}

.source-meta {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 10px;
  margin-top: 8px;
  color: #64748b;
  font-size: 11px;
}

.source-meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  line-height: 1.2;
}

.source-meta svg {
  width: 14px;
  height: 14px;
  flex: none;
}

.source-row-side {
  gap: 8px;
  justify-items: end;
}

.row-action-btn {
  margin-top: 4px;
  border: 1px solid rgba(37, 99, 235, 0.18);
  border-radius: 12px;
  padding: 7px 12px;
  background: rgba(37, 99, 235, 0.08);
  color: #2563eb;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
}

.row-action-btn:hover {
  background: rgba(37, 99, 235, 0.14);
}

.source-metric strong {
  color: #0f172a;
  font-size: 16px;
}

.source-metric span {
  color: #64748b;
  font-size: 11px;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 12px;
  font-weight: 600;
}

.status-chip.is-online,
.status-chip.is-ready {
  color: #166534;
  background: rgba(220, 252, 231, 0.96);
}

.status-chip.is-warning {
  color: #b45309;
  background: rgba(254, 243, 199, 0.98);
}

.status-chip.is-offline,
.status-chip.is-draft {
  color: #475569;
  background: rgba(226, 232, 240, 0.95);
}

@media (max-width: 760px) {
  .search-box {
    max-width: none;
  }

  .toolbar {
    display: grid;
  }

  .toolbar-actions {
    justify-self: end;
  }

  .source-title {
    grid-template-columns: 1fr;
  }

  .source-row-side {
    justify-items: start;
  }
}

@media (max-width: 1180px) {
  .source-stack {
    grid-template-columns: 1fr;
  }
}
</style>
