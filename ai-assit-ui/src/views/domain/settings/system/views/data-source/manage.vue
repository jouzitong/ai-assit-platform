<script setup>
import { ref } from 'vue'
import { ArrowLeft, Download, RefreshRight, Upload } from '@element-plus/icons-vue'
import '../../styles/data-source/manage.scss'
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
  importProgressDialogVisible,
  importJobProgress,
  importJobActive,
  importProgressNoticeVisible,
  importProgressStageLabel,
  importProgressSummary,
  importActionLabel,
  exportDialogVisible,
  exportFormat,
  exportSubmitting,
  knowledgePreviewVisible,
  knowledgePreviewLoading,
  knowledgePreviewError,
  knowledgePreviewData,
  knowledgeSyncSubmitting,
  knowledgeSyncTarget,
  templateSubmitting,
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
  openImportProgressDialog,
  closeImportProgressDialog,
  openExportDialog,
  closeExportDialog,
  openKnowledgePreview,
  closeKnowledgePreview,
  syncKnowledgeBase,
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
          {{ importActionLabel }}
        </button>
        <button type="button" class="toolbar-btn secondary" :disabled="exportSubmitting" @click="openExportDialog">
          <Download :size="16" />
          {{ exportSubmitting ? '导出中...' : '导出' }}
        </button>
        <button type="button" class="toolbar-btn secondary" :disabled="knowledgeSyncSubmitting" @click="syncKnowledgeBase()">
          {{ knowledgeSyncSubmitting && !knowledgeSyncTarget ? '同步中...' : '同步知识库' }}
        </button>
      </div>
    </section>

    <div v-if="importProgressNoticeVisible" class="notice-bar is-success">
      <span>导入任务进行中：{{ importProgressStageLabel }} · {{ importJobProgress.progressPercent }}%</span>
      <button type="button" class="link-btn inline-link" @click="openImportProgressDialog">查看详情</button>
    </div>

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
              <th>中文说明</th>
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
              <td>{{ formatEmpty(item.comment) }}</td>
              <td>{{ item.columns }}</td>
              <td>{{ item.rows }}</td>
              <td>{{ item.partition }}</td>
              <td>{{ item.freshness }}</td>
              <td><span class="status-chip" :class="statusClass(item.status)">{{ item.statusLabel }}</span></td>
              <td class="row-actions-cell">
                <div class="row-actions">
                  <button type="button" class="link-btn">数据查看</button>
                  <button type="button" class="link-btn" @click="openKnowledgePreview(item)">知识库预览</button>
                  <button type="button" class="link-btn" @click="openFieldWorkbench(item)">字段</button>
                  <button type="button" class="link-btn" :disabled="knowledgeSyncSubmitting" @click="syncKnowledgeBase(item)">
                    {{ knowledgeSyncSubmitting && knowledgeSyncTarget === item.name ? '同步中...' : '同步' }}
                  </button>
                  <button type="button" class="link-btn">权限</button>
                </div>
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
<!--        <div class="field-panel-head">-->
<!--          <div class="picker-head">-->
<!--            <p class="eyebrow">字段列表</p>-->
<!--            <h3>{{ selectedTable?.name }}</h3>-->
<!--          </div>-->
<!--          <button type="button" class="toolbar-btn secondary field-back-btn" @click="fieldWorkbenchVisible = false">-->
<!--            返回表列表-->
<!--          </button>-->
<!--        </div>-->

        <div v-if="fieldError" class="table-state is-error">{{ fieldError }}</div>
        <div v-else-if="fieldLoading" class="table-state">正在加载字段列表...</div>
        <div v-else-if="!selectedFields.length" class="table-state">当前表还没有字段元数据。</div>

        <div v-else class="content">
          <section class="content__table-meta">
            <div class="content__section-head">
              <div>
                <p class="eyebrow">表元数据信息</p>
                <h4>{{ selectedTable?.name }}</h4>
              </div>
              <span class="status-chip" :class="statusClass(selectedTable?.status)">{{ selectedTable?.statusLabel }}</span>
            </div>

            <dl class="content__meta-grid">
              <div class="content__meta-item">
                <dt>字段数</dt>
                <dd>{{ selectedTable?.columns ?? '-' }}</dd>
              </div>
              <div class="content__meta-item">
                <dt>数据量</dt>
                <dd>{{ selectedTable?.rows ?? '-' }}</dd>
              </div>
              <div class="content__meta-item">
                <dt>分区键</dt>
                <dd>{{ formatEmpty(selectedTable?.partition) }}</dd>
              </div>
              <div class="content__meta-item">
                <dt>新鲜度</dt>
                <dd>{{ formatEmpty(selectedTable?.freshness) }}</dd>
              </div>
            </dl>
          </section>

          <section class="content__field-list">
            <div class="content__section-head content__section-head--field-list">
              <div>
                <p class="eyebrow">字段列表</p>
                <h4>{{ selectedTable?.name }}</h4>
              </div>
            </div>

            <div class="table-body">
              <table class="config-table field-table">
                <thead>
                  <tr>
                    <th>字段名</th>
                    <th>类型</th>
                    <th>索引</th>
                    <th>关联表</th>
                    <th>描述</th>
                    <th>角色</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="field in selectedFields" :key="field.name">
                    <td><strong>{{ field.name }}</strong></td>
                    <td>{{ field.type }}</td>
                    <td>{{ formatEmpty(field.indexName) }}</td>
                    <td>{{ formatEmpty(field.relatedTable) }}</td>
                    <td>{{ formatEmpty(field.description) }}</td>
                    <td>{{ field.statusLabel }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </div>
      </section>
    </section>

    <div v-if="importDialogVisible" class="modal-mask" @click.self="closeImportDialog">
      <div class="modal-card modal-medium">
        <header class="modal-head">
          <h3>导入元数据</h3>
          <button class="close-btn" type="button" @click="closeImportDialog">×</button>
        </header>

        <div class="modal-body">
          <section class="dialog-section section-panel">
            <header class="section-head">
              <h4>导入文件</h4>
              <p>支持 JSON 和 Excel 模板，导入后会进入后台解析流程。</p>
            </header>

            <div class="form-grid">
              <label class="field-block">
                <span>导入格式</span>
                <select v-model="importFormat" class="field-control">
                  <option value="json">JSON</option>
                  <option value="excel">Excel</option>
                </select>
              </label>
            </div>

            <div
              class="upload-dropzone"
              :class="{ 'is-active': importDragActive }"
              @dragenter.prevent="handleImportDragEnter"
              @dragover.prevent="handleImportDragEnter"
              @dragleave.prevent="handleImportDragLeave"
              @drop.prevent="onFileDrop"
            >
              <p>{{ importFile ? `已选择：${importFile.name}` : `拖拽或选择${importFormatLabel}文件` }}</p>
              <div class="dropzone-actions">
                <button type="button" class="toolbar-btn secondary" @click="triggerFilePicker">选择文件</button>
                <button type="button" class="toolbar-btn secondary" :disabled="templateSubmitting" @click="downloadTemplateWorkbook">
                  {{ templateSubmitting ? '下载中...' : '下载模板' }}
                </button>
              </div>
              <input ref="fileInputRef" class="file-input" type="file" @change="onFileInputChange" />
            </div>

            <p v-if="importError" class="error-banner">{{ importError }}</p>
          </section>
        </div>

        <footer class="modal-foot">
          <button type="button" class="toolbar-secondary-btn" @click="closeImportDialog">取消</button>
          <button type="button" class="toolbar-add-btn" :disabled="importSubmitting" @click="submitImport">
            {{ importSubmitting ? '导入中...' : '开始导入' }}
          </button>
        </footer>
      </div>
    </div>

    <div v-if="importProgressDialogVisible" class="modal-mask" @click.self="closeImportProgressDialog">
      <div class="modal-card modal-medium">
        <header class="modal-head">
          <h3>导入进度</h3>
          <button class="close-btn" type="button" @click="closeImportProgressDialog">×</button>
        </header>

        <div class="modal-body">
          <section class="dialog-section section-panel">
            <header class="section-head">
              <h4>{{ importProgressStageLabel }}</h4>
              <p>{{ importJobProgress.message || '后台正在处理导入任务。' }}</p>
            </header>

            <div class="progress-shell">
              <div class="progress-track">
                <span class="progress-bar" :style="{ width: `${importJobProgress.progressPercent || 0}%` }" />
              </div>
              <strong>{{ importJobProgress.progressPercent || 0 }}%</strong>
            </div>

            <dl class="progress-summary">
              <div>
                <dt>任务状态</dt>
                <dd>{{ importJobProgress.status || '-' }}</dd>
              </div>
              <div>
                <dt>任务 ID</dt>
                <dd>{{ importJobProgress.jobId || '-' }}</dd>
              </div>
              <div>
                <dt>文件名</dt>
                <dd>{{ importJobProgress.fileName || '-' }}</dd>
              </div>
              <div>
                <dt>已处理</dt>
                <dd>{{ importProgressSummary.processed ?? 0 }}</dd>
              </div>
              <div>
                <dt>成功数</dt>
                <dd>{{ importProgressSummary.success ?? 0 }}</dd>
              </div>
              <div>
                <dt>失败数</dt>
                <dd>{{ importProgressSummary.failed ?? 0 }}</dd>
              </div>
            </dl>
          </section>
        </div>
      </div>
    </div>

    <div v-if="exportDialogVisible" class="modal-mask" @click.self="closeExportDialog">
      <div class="modal-card modal-medium">
        <header class="modal-head">
          <h3>导出元数据</h3>
          <button class="close-btn" type="button" @click="closeExportDialog">×</button>
        </header>

        <div class="modal-body">
          <section class="dialog-section section-panel">
            <header class="section-head">
              <h4>导出格式</h4>
              <p>导出当前数据源下的表和字段元数据。</p>
            </header>

            <div class="form-grid">
              <label class="field-block">
                <span>导出格式</span>
                <select v-model="exportFormat" class="field-control">
                  <option value="json">JSON</option>
                  <option value="excel">Excel</option>
                </select>
              </label>
            </div>
          </section>
        </div>

        <footer class="modal-foot">
          <button type="button" class="toolbar-secondary-btn" @click="closeExportDialog">取消</button>
          <button type="button" class="toolbar-add-btn" :disabled="exportSubmitting" @click="exportWorkbook">
            {{ exportSubmitting ? '导出中...' : '开始导出' }}
          </button>
        </footer>
      </div>
    </div>

    <div v-if="knowledgePreviewVisible" class="modal-mask" @click.self="closeKnowledgePreview">
      <div class="modal-card knowledge-preview-modal">
        <header class="modal-head">
          <div>
            <h3>知识库预览</h3>
            <p>{{ knowledgePreviewData.tableName || '当前表' }} · {{ knowledgePreviewData.type || 'markdown' }}</p>
          </div>
          <button class="close-btn" type="button" @click="closeKnowledgePreview">×</button>
        </header>

        <div v-if="knowledgePreviewError" class="error-banner">{{ knowledgePreviewError }}</div>
        <div v-else-if="knowledgePreviewLoading" class="table-state">正在加载知识库预览...</div>
        <div v-else class="knowledge-preview-body">
          <pre class="knowledge-preview-content">{{ knowledgePreviewData.content || '暂无预览内容。' }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>
