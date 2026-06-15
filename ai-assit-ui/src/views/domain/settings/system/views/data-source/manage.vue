<script setup>
import { ref } from 'vue'
import { ArrowLeft, Download, Plus, RefreshRight, Upload } from '@element-plus/icons-vue'
import '../../styles/data-source/manage.css'
import { useDataSourceManagePage } from '../../service/data-source/manage'

const {
  currentSource,
  currentSourceList,
  pagedTables,
  currentTables,
  fieldWorkbenchVisible,
  pageSizeOptions,
  pagination,
  pageSummary,
  totalPages,
  selectedTableName,
  selectedTable,
  selectedFields,
  sourceLoading,
  tableLoading,
  fieldLoading,
  sourceError,
  tableError,
  fieldError,
  importDialogVisible,
  importDragActive,
  importFile,
  importError,
  importFormat,
  importSubmitting,
  exportDialogVisible,
  exportFormat,
  exportSubmitting,
  templateSubmitting,
  notice,
  handleSourceChange,
  handlePageChange,
  handlePageSizeChange,
  openFieldWorkbench,
  selectTable,
  formatEmpty,
  goBack,
  statusClass,
  refreshPage,
  openImportDialog,
  closeImportDialog,
  openExportDialog,
  closeExportDialog,
  handleImportDragEnter,
  handleImportDragLeave,
  handleImportFile,
  submitImport,
  exportWorkbook,
  downloadTemplateWorkbook,
  importFormatLabel
} = useDataSourceManagePage()

const fileInputRef = ref(null)

function triggerFilePicker() {
  fileInputRef.value?.click()
}

function onFileInputChange(event) {
  handleImportFile(event.target.files?.[0] ?? null)
  event.target.value = ''
}

function onFileDrop(event) {
  handleImportFile(event.dataTransfer?.files?.[0] ?? null)
}
</script>

<template>
  <div class="data-source-manage-page">
    <div v-if="notice.text" :class="['notice-bar', notice.type === 'error' ? 'is-error' : 'is-success']">
      {{ notice.text }}
    </div>

    <section class="content-head compact">
      <div class="head-copy">
        <button type="button" class="back-btn" @click="goBack">
          <ArrowLeft :size="16" />
          返回列表
        </button>
        <div>
          <p class="eyebrow">数据表管理</p>
          <select class="source-switcher" :value="currentSource?.key || ''" aria-label="切换数据源" @change="handleSourceChange">
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
        <button type="button" class="toolbar-btn secondary" @click="refreshPage">
          <RefreshRight :size="16" />
          刷新
        </button>
        <button type="button" class="toolbar-btn secondary" @click="openImportDialog">
          <Upload :size="16" />
          导入
        </button>
        <button type="button" class="toolbar-btn secondary" :disabled="exportSubmitting" @click="openExportDialog">
          <Download :size="16" />
          {{ exportSubmitting ? '导出中...' : '导出' }}
        </button>
        <button type="button" class="toolbar-btn primary">
          <Plus :size="16" />
          新增表
        </button>
      </div>
    </section>

    <section v-if="!fieldWorkbenchVisible" class="table-card">
      <div v-if="sourceError" class="table-state is-error">{{ sourceError }}</div>
      <div v-else-if="tableError" class="table-state is-error">{{ tableError }}</div>
      <div v-else-if="sourceLoading || tableLoading" class="table-state">正在加载数据表列表...</div>
      <div v-else-if="!pagedTables.length" class="table-state">当前数据源下还没有数据表元数据。</div>

      <div v-else class="table-body">
        <table class="config-table">
          <thead>
            <tr>
              <th>表名</th>
              <th>字段数</th>
              <th>数据量</th>
              <th>分区键</th>
              <th>新鲜度</th>
              <th>字段角色</th>
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
      <aside class="table-picker compact">
        <div class="picker-head">
          <p class="eyebrow">表切换</p>
          <h3>表名称</h3>
        </div>

        <div class="table-list">
          <button
            v-for="item in currentTables"
            :key="item.name"
            type="button"
            class="table-item"
            :class="{ 'is-active': item.name === selectedTableName }"
            @click="selectTable(item)"
          >
            <strong>{{ item.name }}</strong>
            <span>{{ item.columns }} 字段 · {{ item.statusLabel }}</span>
          </button>
        </div>
      </aside>

      <section class="field-panel">
        <div class="field-panel-head">
          <div class="picker-head">
            <p class="eyebrow">字段列表</p>
            <h3>{{ selectedTable?.name }}</h3>
          </div>
          <button type="button" class="toolbar-btn secondary field-back-btn" @click="fieldWorkbenchVisible = false">
            返回表列表
          </button>
        </div>

        <div v-if="fieldError" class="table-state is-error">{{ fieldError }}</div>
        <div v-else-if="fieldLoading" class="table-state">正在加载字段列表...</div>
        <div v-else-if="!selectedFields.length" class="table-state">当前表下还没有字段元数据。</div>

        <table v-else class="field-table">
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

    <div v-if="importDialogVisible" class="modal-mask" @click.self="closeImportDialog">
      <div class="modal-card import-modal">
        <header class="modal-head">
          <div>
            <h3>导入表结构元数据</h3>
            <p>
              支持 JSON 和 Excel 导入。JSON 适合结构化回传，Excel 适合按 `表说明`、`字段说明`、`索引说明` 三个 sheet 维护。
            </p>
          </div>
          <button class="close-btn" type="button" @click="closeImportDialog">×</button>
        </header>

        <p v-if="importError" class="error-banner">{{ importError }}</p>

        <div class="export-format-list">
          <label class="export-format-option" :class="{ 'is-active': importFormat === 'json' }">
            <input v-model="importFormat" type="radio" name="import-format" value="json" />
            <div>
              <strong>JSON 导入</strong>
              <span>上传 `.json` 文件，适合导出回传、版本管理和批量同步。</span>
            </div>
          </label>

          <label class="export-format-option" :class="{ 'is-active': importFormat === 'excel' }">
            <input v-model="importFormat" type="radio" name="import-format" value="excel" />
            <div>
              <strong>Excel 导入</strong>
              <span>上传 `.xlsx` 文件，按三张 sheet 做新增或更新。</span>
            </div>
          </label>
        </div>

        <button
          type="button"
          class="drop-zone"
          :class="{ 'is-dragover': importDragActive }"
          @click="triggerFilePicker"
          @dragenter.prevent="handleImportDragEnter"
          @dragover.prevent="handleImportDragEnter"
          @dragleave.prevent="handleImportDragLeave"
          @drop.prevent="onFileDrop"
        >
          <Upload :size="22" />
          <strong>{{ importFile ? importFile.name : `点击选择${importFormatLabel()}文件，或直接拖拽到这里` }}</strong>
          <span v-if="importFormat === 'json'">支持 `.json`，结构与导出 JSON 一致，导入时会按自然键对表、字段、索引做新增或更新。</span>
          <span v-else>支持 `.xlsx`，需包含 `表说明`、`字段说明`、`索引说明` 三个 sheet，导入时会按自然键对表、字段、索引做新增或更新。</span>
        </button>

        <input ref="fileInputRef" class="hidden-file-input" type="file" :accept="importFormat === 'json' ? '.json' : '.xlsx'" @change="onFileInputChange" />

        <footer class="modal-actions">
          <button class="action-btn secondary-btn" type="button" :disabled="templateSubmitting" @click="downloadTemplateWorkbook">
            {{ templateSubmitting ? '下载中...' : `下载${importFormatLabel()}模板` }}
          </button>
          <button class="action-btn" type="button" @click="closeImportDialog">取消</button>
          <button class="action-btn primary" type="button" :disabled="importSubmitting" @click="submitImport">
            {{ importSubmitting ? '导入中...' : '开始导入' }}
          </button>
        </footer>
      </div>
    </div>

    <div v-if="exportDialogVisible" class="modal-mask" @click.self="closeExportDialog">
      <div class="modal-card export-modal">
        <header class="modal-head">
          <div>
            <h3>导出元数据</h3>
            <p>选择导出格式。默认使用 JSON，便于再次导入或版本管理。</p>
          </div>
          <button class="close-btn" type="button" @click="closeExportDialog">×</button>
        </header>

        <div class="export-format-list">
          <label class="export-format-option" :class="{ 'is-active': exportFormat === 'json' }">
            <input v-model="exportFormat" type="radio" name="export-format" value="json" />
            <div>
              <strong>JSON</strong>
              <span>结构化导出，适合回传、对比和二次导入。</span>
            </div>
          </label>

          <label class="export-format-option" :class="{ 'is-active': exportFormat === 'excel' }">
            <input v-model="exportFormat" type="radio" name="export-format" value="excel" />
            <div>
              <strong>Excel</strong>
              <span>保留现有工作簿格式，适合人工查看和线下编辑。</span>
            </div>
          </label>
        </div>

        <footer class="modal-actions">
          <button class="action-btn" type="button" @click="closeExportDialog">取消</button>
          <button class="action-btn primary" type="button" :disabled="exportSubmitting" @click="exportWorkbook">
            {{ exportSubmitting ? '导出中...' : '开始导出' }}
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>
