<script setup>
import { EditPen, Plus, RefreshRight, RemoveFilled, Search, SwitchButton } from '@element-plus/icons-vue'
import '../styles/params.scss'
import { useSystemParamsPage } from '../service/params'

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
  settingList,
  valueTypeOptions,
  enabledOptions,
  pageSizeOptions,
  pagination,
  pageSummary,
  totalPages,
  openCreateDialog,
  openEditDialog,
  closeDialog,
  submitForm,
  toggleSettingStatus,
  confirmDelete,
  loadSystemSettings,
  handleSearch,
  resetFilters,
  handlePageChange,
  handlePageSizeChange,
  formatDateTime
} = useSystemParamsPage()
</script>

<template>
  <div class="params-page">
    <header class="content-head">
      <div class="head-copy">
        <h1>系统配置工作台</h1>
      </div>

      <button class="create-pill" type="button" aria-label="新增" @click="openCreateDialog">
        <Plus :size="26" />
      </button>
    </header>

    <section class="workspace-card">
      <div class="toolbar-grid">
        <label class="search-box">
          <Search :size="16" />
          <input v-model="keyword" type="text" placeholder="搜索配置 Key、说明或配置值" @keyup.enter="handleSearch" />
        </label>

        <select v-model="filters.valueType" class="field-control">
          <option v-for="item in valueTypeOptions" :key="item.value || 'all'" :value="item.value">{{ item.label }}</option>
        </select>

        <select v-model="filters.enabled" class="field-control">
          <option v-for="item in enabledOptions" :key="item.value || 'all'" :value="item.value">{{ item.label }}</option>
        </select>

        <div class="toolbar-actions">
          <button class="action-btn primary" type="button" @click="handleSearch">查询</button>
          <button class="action-btn" type="button" @click="resetFilters">重置</button>
          <button class="action-btn" type="button" @click="loadSystemSettings">
            <RefreshRight :size="15" />
            刷新
          </button>
        </div>
      </div>

      <section class="table-panel">
        <div v-if="loading" class="placeholder-panel">
          <p>正在加载 `/user/api/v1/system-settings/_search` 的配置列表...</p>
        </div>

        <div v-else-if="errorMessage" class="placeholder-panel is-error">
          <p>{{ errorMessage }}</p>
        </div>

        <div v-else-if="!settingList.length" class="placeholder-panel">
          <p>当前没有匹配到系统配置，可以先新增一条全局参数。</p>
        </div>

        <div v-else class="table-wrap">
          <table class="settings-table">
            <colgroup>
              <col class="key-column" />
              <col class="desc-column" />
              <col class="type-column" />
              <col class="value-column" />
              <col class="status-column" />
              <col class="time-column" />
              <col class="user-column" />
              <col class="action-column" />
            </colgroup>
            <thead>
              <tr>
                <th>配置 Key</th>
                <th>说明</th>
                <th>值类型</th>
                <th>配置值</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>修改人</th>
                <th class="action-column">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in settingList" :key="item.id">
                <td class="key-cell">{{ item.settingKey }}</td>
                <td>{{ item.description }}</td>
                <td><span class="type-badge">{{ item.valueTypeLabel }}</span></td>
                <td><code class="value-code">{{ item.previewValue }}</code></td>
                <td>
                  <span class="state-chip" :class="item.enabled ? 'is-on' : 'is-off'">
                    {{ item.statusLabel }}
                  </span>
                </td>
                <td>{{ formatDateTime(item.updateTime) }}</td>
                <td>{{ item.lastModifiedBy }}</td>
                <td>
                  <div class="row-actions">
                    <button class="row-action" type="button" @click="toggleSettingStatus(item)">
                      <SwitchButton :size="15" />
                      {{ item.enabled ? '停用' : '启用' }}
                    </button>
                    <button class="row-action" type="button" @click="openEditDialog(item)">
                      <EditPen :size="15" />
                      编辑
                    </button>
                    <button class="row-action danger" type="button" @click="confirmDelete(item)">
                      <RemoveFilled :size="15" />
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
      <div class="modal-card">
        <header class="modal-head">
          <h3>{{ dialogMode === 'create' ? '新增系统配置' : '编辑系统配置' }}</h3>
          <button class="close-btn" type="button" @click="closeDialog">×</button>
        </header>

        <p v-if="dialogError" class="error-banner">{{ dialogError }}</p>

        <div class="form-grid">
          <label class="field-block">
            <span>配置 Key</span>
            <input v-model="form.settingKey" class="field-control" type="text" :disabled="dialogMode === 'edit'" placeholder="例如 system.theme.primary" />
          </label>

          <label class="field-block">
            <span>值类型</span>
            <select v-model="form.valueType" class="field-control">
              <option v-for="item in valueTypeOptions.slice(1)" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>

          <label class="field-block full-span">
            <span>配置说明</span>
            <input v-model="form.description" class="field-control" type="text" placeholder="说明这个配置控制的具体行为" />
          </label>

          <label class="field-block full-span">
            <span>配置值</span>
            <textarea
              v-model="form.settingValue"
              class="field-control textarea-control"
              rows="7"
              :placeholder="form.valueType === 'JSON' ? '{\n  &quot;enabled&quot;: true\n}' : '请输入配置值'"
            />
          </label>

          <label class="switch-block full-span">
            <input v-model="form.enabled" type="checkbox" />
            <span>保存后立即启用这个配置</span>
          </label>
        </div>

        <footer class="modal-actions">
          <button class="action-btn" type="button" @click="closeDialog">取消</button>
          <button class="action-btn primary" type="button" :disabled="saving" @click="submitForm">
            {{ saving ? '保存中...' : '保存配置' }}
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>
