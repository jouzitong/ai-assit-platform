<script setup>
import { Delete, EditPen, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { useAppMetadataPage } from '../service/app-metadata'

const {
  loading,
  saving,
  errorMessage,
  dialogError,
  dialogVisible,
  dialogMode,
  keyword,
  filters,
  form,
  pageList,
  categoryOptions,
  statusOptions,
  pageSizeOptions,
  pagination,
  pageSummary,
  totalPages,
  loadPageList,
  handleSearch,
  resetFilters,
  handlePageChange,
  handlePageSizeChange,
  openCreateDialog,
  openEditDialog,
  closeDialog,
  submitForm,
  confirmDelete,
  formatDateTime,
  resolveStatusClass
} = useAppMetadataPage()
</script>

<template>
  <div class="app-metadata-page">
    <header class="content-head">
      <div class="head-copy">
        <p class="eyebrow">应用元数据配置</p>
        <h1>Render 页面元数据工作台</h1>
        <p class="section-desc">直接对接 `RenderPageManageController`，统一维护页面编码、名称、分类、状态和 JSON 内容。</p>
      </div>

      <button class="create-pill" type="button" aria-label="新增页面" @click="openCreateDialog">
        <Plus :size="26" />
      </button>
    </header>

    <section class="workspace-card">
      <div class="toolbar-grid">
        <label class="search-box">
          <Search :size="16" />
          <input v-model="keyword" type="text" placeholder="搜索页面编码或名称" @keyup.enter="handleSearch" />
        </label>

        <select v-model="filters.categoryCode" class="field-control">
          <option value="">全部分类</option>
          <option v-for="item in categoryOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>

        <select v-model="filters.status" class="field-control">
          <option v-for="item in statusOptions" :key="item.value || 'all'" :value="item.value">{{ item.label }}</option>
        </select>

        <div class="toolbar-actions">
          <button class="action-btn primary" type="button" @click="handleSearch">查询</button>
          <button class="action-btn" type="button" @click="resetFilters">重置</button>
          <button class="action-btn" type="button" @click="loadPageList">
            <RefreshRight :size="15" />
            刷新
          </button>
        </div>
      </div>

      <section class="table-panel">
        <div v-if="loading" class="placeholder-panel">
          <p>正在加载 `/api/v1/render/pages/page` 的页面元数据列表...</p>
        </div>

        <div v-else-if="errorMessage" class="placeholder-panel is-error">
          <p>{{ errorMessage }}</p>
        </div>

        <div v-else-if="!pageList.length" class="placeholder-panel">
          <p>当前没有匹配到应用元数据，可以先新建一个 Render 页面。</p>
        </div>

        <div v-else class="table-wrap">
          <table class="metadata-table">
            <colgroup>
              <col class="code-column" />
              <col class="name-column" />
              <col class="category-column" />
              <col class="status-column" />
              <col class="time-column" />
              <col class="content-column" />
              <col class="action-column" />
            </colgroup>
            <thead>
              <tr>
                <th>页面编码</th>
                <th>页面名称</th>
                <th>分类</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>内容预览</th>
                <th class="action-column">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in pageList" :key="item.id">
                <td class="code-cell">{{ item.code }}</td>
                <td>{{ item.name }}</td>
                <td>{{ item.categoryLabel }}</td>
                <td>
                  <span class="state-chip" :class="resolveStatusClass(item.status)">{{ item.statusLabel }}</span>
                </td>
                <td>{{ formatDateTime(item.updateTime) }}</td>
                <td>
                  <code class="content-preview">{{ item.contentPreview }}</code>
                </td>
                <td>
                  <div class="row-actions">
                    <button class="row-action" type="button" @click="openEditDialog(item)">
                      <EditPen :size="15" />
                      编辑
                    </button>
                    <button class="row-action danger" type="button" @click="confirmDelete(item)">
                      <Delete :size="15" />
                      删除
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

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
            <button class="action-btn" type="button" :disabled="pagination.page >= totalPages" @click="handlePageChange(pagination.page + 1)">
              下一页
            </button>
          </div>
        </div>
      </footer>
    </section>

    <div v-if="dialogVisible" class="modal-mask" @click.self="closeDialog">
      <div class="modal-card modal-wide">
        <header class="modal-head">
          <h3>{{ dialogMode === 'create' ? '新增应用元数据' : '编辑应用元数据' }}</h3>
          <button class="close-btn" type="button" @click="closeDialog">×</button>
        </header>

        <p v-if="dialogError" class="error-banner">{{ dialogError }}</p>

        <div class="form-grid two-column">
          <label class="field-block">
            <span>页面编码</span>
            <input v-model="form.code" class="field-control" type="text" placeholder="例如 app.home.dashboard" />
          </label>

          <label class="field-block">
            <span>页面名称</span>
            <input v-model="form.name" class="field-control" type="text" placeholder="例如 首页看板" />
          </label>

          <label class="field-block">
            <span>页面分类</span>
            <select v-model="form.categoryCode" class="field-control">
              <option value="">未分类</option>
              <option v-for="item in categoryOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>

          <label class="field-block">
            <span>页面状态</span>
            <select v-model="form.status" class="field-control">
              <option v-for="item in statusOptions.slice(1)" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>

          <label class="field-block full-span">
            <span>页面内容(JSON)</span>
            <textarea
              v-model="form.content"
              class="field-control textarea-control code-area"
              rows="16"
              placeholder="{&#10;  &quot;title&quot;: &quot;首页&quot;&#10;}"
            />
          </label>
        </div>

        <footer class="modal-actions">
          <button class="action-btn" type="button" @click="closeDialog">取消</button>
          <button class="action-btn primary" type="button" :disabled="saving" @click="submitForm">
            {{ saving ? '保存中...' : '保存页面' }}
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.app-metadata-page {
  display: grid;
  gap: 18px;
  min-height: 0;
  padding: 18px;
}

.head-copy {
  display: grid;
  gap: 8px;
}

.create-pill {
  width: 56px;
  height: 56px;
  border: 0;
  border-radius: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  background: linear-gradient(135deg, #0f766e, #0ea5e9);
  box-shadow: 0 14px 30px rgba(14, 165, 233, 0.18);
  cursor: pointer;
}

.toolbar-grid {
  display: grid;
  grid-template-columns: minmax(260px, 1.4fr) minmax(180px, 0.9fr) minmax(160px, 0.8fr) auto;
  gap: 12px;
  align-items: center;
}

.toolbar-actions,
.page-controls,
.pager,
.row-actions,
.modal-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar-actions {
  justify-content: flex-end;
}

.table-wrap {
  overflow: auto;
}

.metadata-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.metadata-table th,
.metadata-table td {
  padding: 14px 12px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
  text-align: left;
  vertical-align: top;
}

.metadata-table th {
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.code-cell {
  font-family: 'SFMono-Regular', 'Menlo', monospace;
  color: #0f172a;
  word-break: break-all;
}

.content-preview {
  display: -webkit-box;
  overflow: hidden;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.state-chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.state-chip.is-draft {
  color: #9a3412;
  background: #ffedd5;
}

.state-chip.is-published {
  color: #166534;
  background: #dcfce7;
}

.state-chip.is-disabled {
  color: #991b1b;
  background: #fee2e2;
}

.row-action {
  border: 0;
  background: transparent;
  color: #2563eb;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

.row-action.danger {
  color: #dc2626;
}

.pagination-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding-top: 14px;
}

.page-summary,
.pager-indicator {
  color: #64748b;
  font-size: 13px;
}

.field-control.code-area {
  min-height: 320px;
  font-family: 'SFMono-Regular', 'Menlo', monospace;
}

.modal-wide {
  width: min(860px, calc(100vw - 48px));
}

.code-column {
  width: 16%;
}

.name-column {
  width: 12%;
}

.category-column {
  width: 14%;
}

.status-column {
  width: 10%;
}

.time-column {
  width: 14%;
}

.content-column {
  width: 24%;
}

.action-column {
  width: 10%;
}

@media (max-width: 1180px) {
  .toolbar-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .pagination-bar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
