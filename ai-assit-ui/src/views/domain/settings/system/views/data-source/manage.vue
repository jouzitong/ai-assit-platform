<script setup>
import { ArrowLeft, Download, Plus, RefreshRight, Upload } from '@element-plus/icons-vue'
import '../../styles/data-source/manage.css'
import { useDataSourceManagePage } from '../../service/data-source/manage'

const {
  currentSource,
  currentTables,
  pagedTables,
  fieldWorkbenchVisible,
  pageSizeOptions,
  pagination,
  pageSummary,
  totalPages,
  selectedTableName,
  selectedTable,
  selectedFields,
  handleSourceChange,
  handlePageChange,
  handlePageSizeChange,
  openFieldWorkbench,
  selectTable,
  formatEmpty,
  goBack,
  statusClass
} = useDataSourceManagePage()
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
            <option v-for="source in currentSourceList" :key="source.key" :value="source.key">
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
