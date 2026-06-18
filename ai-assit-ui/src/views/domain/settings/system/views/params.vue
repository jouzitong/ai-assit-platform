<script setup>
import { CircleCheckFilled, EditPen, Plus, RefreshRight, RemoveFilled, Search, Setting, SwitchButton } from '@element-plus/icons-vue'
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
  stats,
  settingList,
  selectedSetting,
  valueTypeOptions,
  enabledOptions,
  pageSizeOptions,
  pagination,
  pageSummary,
  totalPages,
  selectSetting,
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
        <p class="crumb">系统设置 / 系统参数</p>
        <h1>系统配置工作台</h1>
        <p class="desc">集中维护全局配置项，覆盖 Key、值类型、运行值和启停状态，适合作为系统级参数面板的第一版 CRUD 页面。</p>
      </div>

      <button class="create-pill" type="button" @click="openCreateDialog">
        <Plus :size="16" />
        新增配置
      </button>
    </header>

    <section class="stats-row">
      <article v-for="item in stats" :key="item.label" class="stat-card">
        <strong>{{ item.value }}</strong>
        <span>{{ item.label }}</span>
      </article>
    </section>

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

      <div class="workspace-grid">
        <section class="list-panel">
          <div class="panel-head">
            <div>
              <p class="panel-eyebrow">配置清单</p>
              <h3>当前页 {{ settingList.length }} 条</h3>
            </div>
          </div>

          <div v-if="loading" class="placeholder-panel">
            <p>正在加载 `/user/api/v1/system-settings/_search` 的配置列表...</p>
          </div>

          <div v-else-if="errorMessage" class="placeholder-panel is-error">
            <p>{{ errorMessage }}</p>
          </div>

          <div v-else-if="!settingList.length" class="placeholder-panel">
            <p>当前没有匹配到系统配置，可以先新增一条全局参数。</p>
          </div>

          <div v-else class="setting-list">
            <article
              v-for="item in settingList"
              :key="item.id"
              class="setting-card"
              :class="{ active: selectedSetting?.id === item.id }"
              role="button"
              tabindex="0"
              @click="selectSetting(item.id)"
              @keydown.enter.prevent="selectSetting(item.id)"
              @keydown.space.prevent="selectSetting(item.id)"
            >
              <div class="setting-card-head">
                <div class="title-group">
                  <strong>{{ item.settingKey }}</strong>
                  <span class="type-badge">{{ item.valueTypeLabel }}</span>
                </div>
                <span class="state-chip" :class="item.enabled ? 'is-on' : 'is-off'">
                  {{ item.statusLabel }}
                </span>
              </div>

              <p class="setting-desc">{{ item.description }}</p>

              <div class="value-preview">
                <span class="preview-label">当前值</span>
                <code>{{ item.previewValue }}</code>
              </div>

              <div class="meta-row">
                <span>{{ formatDateTime(item.updateTime) }}</span>
                <span>{{ item.lastModifiedBy }}</span>
              </div>
            </article>
          </div>
        </section>

        <section class="detail-panel">
          <div v-if="selectedSetting" class="detail-stack">
            <header class="detail-hero">
              <div class="detail-copy">
                <p class="panel-eyebrow">配置详情</p>
                <h3>{{ selectedSetting.settingKey }}</h3>
                <p>{{ selectedSetting.description }}</p>
              </div>

              <div class="detail-actions">
                <button class="action-btn" type="button" @click="toggleSettingStatus(selectedSetting)">
                  <SwitchButton :size="15" />
                  {{ selectedSetting.enabled ? '停用' : '启用' }}
                </button>
                <button class="action-btn" type="button" @click="openEditDialog(selectedSetting)">
                  <EditPen :size="15" />
                  编辑
                </button>
                <button class="action-btn danger" type="button" @click="confirmDelete(selectedSetting)">
                  <RemoveFilled :size="15" />
                  删除
                </button>
              </div>
            </header>

            <section class="detail-card emphasis">
              <div class="detail-card-head">
                <span class="card-dot"><Setting :size="16" /></span>
                <div>
                  <h4>配置值</h4>
                  <p>当前系统实际生效的参数内容。</p>
                </div>
              </div>
              <pre class="detail-value">{{ selectedSetting.raw?.settingValue || '未配置' }}</pre>
            </section>

            <section class="detail-meta-grid">
              <article class="detail-card">
                <div class="detail-card-head">
                  <span class="card-dot success"><CircleCheckFilled :size="16" /></span>
                  <div>
                    <h4>基础属性</h4>
                    <p>识别字段和启用状态。</p>
                  </div>
                </div>

                <dl class="detail-list">
                  <div>
                    <dt>值类型</dt>
                    <dd>{{ selectedSetting.valueTypeLabel }}</dd>
                  </div>
                  <div>
                    <dt>当前状态</dt>
                    <dd>{{ selectedSetting.statusLabel }}</dd>
                  </div>
                  <div>
                    <dt>配置 ID</dt>
                    <dd>#{{ selectedSetting.id }}</dd>
                  </div>
                </dl>
              </article>

              <article class="detail-card">
                <div class="detail-card-head">
                  <span class="card-dot muted"><RefreshRight :size="16" /></span>
                  <div>
                    <h4>变更信息</h4>
                    <p>用于确认最近维护轨迹。</p>
                  </div>
                </div>

                <dl class="detail-list">
                  <div>
                    <dt>更新时间</dt>
                    <dd>{{ formatDateTime(selectedSetting.updateTime) }}</dd>
                  </div>
                  <div>
                    <dt>最后修改人</dt>
                    <dd>{{ selectedSetting.lastModifiedBy }}</dd>
                  </div>
                </dl>
              </article>
            </section>
          </div>

          <div v-else class="placeholder-panel subtle">
            <p>从左侧选择一条系统配置后，在这里查看详情和执行操作。</p>
          </div>
        </section>
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
