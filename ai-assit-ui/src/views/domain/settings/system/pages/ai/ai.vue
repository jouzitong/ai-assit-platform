<script setup>
import './styles/index.css'
import { useAiPage } from './service'

const {
  activeTab,
  loading,
  providerFilters,
  modelFilters,
  providerPagination,
  modelPagination,
  providerList,
  modelList,
  providerOptions,
  providerDialogVisible,
  providerDialogMode,
  providerError,
  providerForm,
  modelDialogVisible,
  modelDialogMode,
  modelError,
  modelForm,
  enabledOptions,
  pageSizeOptions,
  notice,
  currentStats,
  currentPage,
  currentSize,
  pageSummary,
  totalPages,
  openProviderCreate,
  openProviderEdit,
  openModelEdit,
  submitProviderForm,
  submitModelForm,
  toggleProviderStatus,
  toggleModelStatus,
  confirmDeleteProvider,
  confirmDeleteModel,
  resetProviderFilters,
  resetModelFilters,
  handleSearch,
  handlePageChange,
  handlePageSizeChange,
  openCreateByTab,
  formatDateTime,
  tagList
} = useAiPage()
</script>

<template>
  <div class="ai-page">
    <header class="content-head">
      <div class="head-copy">
        <p class="crumb">系统设置 / AI 接入</p>
        <h1>AI 元数据维护</h1>
        <p class="desc">页面只维护真实的 Provider 与 Model 配置。Model 编辑同时管理内部凭证配置。</p>
      </div>

      <button class="create-pill" type="button" @click="openCreateByTab">
        {{ activeTab === 'provider' ? '新增 Provider' : '新增 Model' }}
      </button>
    </header>

    <section class="stats-row">
      <article v-for="item in currentStats" :key="item.label" class="stat-card">
        <strong>{{ item.value }}</strong>
        <span>{{ item.label }}</span>
      </article>
    </section>

    <div v-if="notice.text" :class="['notice-bar', notice.type === 'error' ? 'is-error' : 'is-success']">
      {{ notice.text }}
    </div>

    <section class="workspace-card">
      <div class="tab-strip">
        <button
          class="tab-pill"
          :class="{ active: activeTab === 'provider' }"
          type="button"
          @click="activeTab = 'provider'"
        >
          Provider 管理
        </button>
        <button
          class="tab-pill"
          :class="{ active: activeTab === 'model' }"
          type="button"
          @click="activeTab = 'model'"
        >
          Model 管理
        </button>
      </div>

      <div v-if="activeTab === 'provider'" class="panel-shell">
        <div class="toolbar-grid provider-toolbar">
          <input
            v-model="providerFilters.providerCode"
            class="field-control"
            type="text"
            placeholder="按 Provider 编码检索"
            @keyup.enter="handleSearch"
          />

          <select v-model="providerFilters.enabled" class="field-control">
            <option v-for="item in enabledOptions" :key="item.label" :value="item.value">{{ item.label }}</option>
          </select>

          <div class="toolbar-actions">
            <button class="action-btn primary" type="button" @click="handleSearch">查询</button>
            <button class="action-btn" type="button" @click="resetProviderFilters">重置</button>
          </div>
        </div>

        <div class="table-card">
          <div v-if="loading.provider" class="table-state">正在加载 Provider 列表...</div>

          <template v-else>
            <div class="table-scroll">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>Provider 编码</th>
                    <th>Provider 名称</th>
                    <th>基础地址</th>
                    <th>连接超时</th>
                    <th>读取超时</th>
                    <th>状态</th>
                    <th>更新时间</th>
                    <th>备注</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in providerList" :key="row.id">
                    <td>{{ row.providerCode }}</td>
                    <td>{{ row.providerName }}</td>
                    <td class="ellipsis">{{ row.baseUrl }}</td>
                    <td>{{ row.connectTimeoutMs }} ms</td>
                    <td>{{ row.readTimeoutMs }} ms</td>
                    <td>
                      <button
                        class="status-btn"
                        :class="row.enabled ? 'is-on' : 'is-off'"
                        type="button"
                        @click="toggleProviderStatus(row)"
                      >
                        {{ row.enabled ? '启用' : '停用' }}
                      </button>
                    </td>
                    <td>{{ formatDateTime(row.updateTime) }}</td>
                    <td class="ellipsis">{{ row.remark || '-' }}</td>
                    <td>
                      <div class="row-actions">
                        <button class="link-btn" type="button" @click="openProviderEdit(row)">编辑</button>
                        <button class="link-btn danger" type="button" @click="confirmDeleteProvider(row)">删除</button>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="!providerList.length">
                    <td colspan="9" class="empty-cell">暂无 Provider 数据</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
        </div>
      </div>

      <div v-else class="panel-shell">
        <div class="toolbar-grid model-toolbar">
          <input
            v-model="modelFilters.keyword"
            class="field-control"
            type="text"
            placeholder="搜索模型编码、模型名称、API Model、凭证编码"
            @keyup.enter="handleSearch"
          />

          <select v-model="modelFilters.providerCode" class="field-control">
            <option value="">全部 Provider</option>
            <option v-for="item in providerOptions" :key="item.id" :value="item.providerCode">
              {{ item.providerName }} ({{ item.providerCode }})
            </option>
          </select>

          <select v-model="modelFilters.enabled" class="field-control">
            <option v-for="item in enabledOptions" :key="item.label" :value="item.value">{{ item.label }}</option>
          </select>

          <div class="toolbar-actions">
            <button class="action-btn primary" type="button" @click="handleSearch">查询</button>
            <button class="action-btn" type="button" @click="resetModelFilters">重置</button>
          </div>
        </div>

        <div class="table-card">
          <div v-if="loading.model" class="table-state">正在加载 Model 列表...</div>

          <template v-else>
            <div class="table-scroll">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>模型编码</th>
                    <th>模型名称</th>
                    <th>Provider</th>
                    <th>API Model</th>
                    <th>能力标签</th>
                    <th>状态</th>
                    <th>优先级</th>
                    <th>凭证编码</th>
                    <th>脱敏 Key</th>
                    <th>凭证状态</th>
                    <th>更新时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in modelList" :key="row.id">
                    <td>{{ row.modelCode }}</td>
                    <td>{{ row.modelName }}</td>
                    <td>
                      <div class="provider-cell">
                        <strong>{{ row.providerName || row.providerCode }}</strong>
                        <span>{{ row.providerCode }}</span>
                      </div>
                    </td>
                    <td>{{ row.apiModel }}</td>
                    <td>
                      <div class="tag-list">
                        <span v-for="tag in tagList(row.capabilityTags)" :key="tag" class="soft-tag">{{ tag }}</span>
                        <span v-if="!tagList(row.capabilityTags).length">-</span>
                      </div>
                    </td>
                    <td>
                      <button
                        class="status-btn"
                        :class="row.enabled ? 'is-on' : 'is-off'"
                        type="button"
                        @click="toggleModelStatus(row)"
                      >
                        {{ row.enabled ? '启用' : '停用' }}
                      </button>
                    </td>
                    <td>{{ row.priority ?? '-' }}</td>
                    <td>{{ row.credentialCode || '-' }}</td>
                    <td>{{ row.apiKeyMasked || '-' }}</td>
                    <td>
                      <span class="state-chip" :class="row.credentialEnabled ? 'is-on' : 'is-off'">
                        {{ row.credentialEnabled ? '启用' : '停用' }}
                      </span>
                    </td>
                    <td>{{ formatDateTime(row.updateTime) }}</td>
                    <td>
                      <div class="row-actions">
                        <button class="link-btn" type="button" @click="openModelEdit(row)">编辑</button>
                        <button class="link-btn danger" type="button" @click="confirmDeleteModel(row)">删除</button>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="!modelList.length">
                    <td colspan="12" class="empty-cell">暂无 Model 数据</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
        </div>
      </div>

      <footer class="pagination-bar">
        <span class="page-summary">{{ pageSummary }}</span>
        <div class="page-controls">
          <select class="field-control page-size" :value="currentSize" @change="handlePageSizeChange">
            <option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }} / 页</option>
          </select>

          <div class="pager">
            <button class="action-btn" type="button" :disabled="currentPage <= 1" @click="handlePageChange(currentPage - 1)">
              上一页
            </button>
            <span class="pager-indicator">{{ currentPage }} / {{ totalPages }}</span>
            <button
              class="action-btn"
              type="button"
              :disabled="currentPage >= totalPages"
              @click="handlePageChange(currentPage + 1)"
            >
              下一页
            </button>
          </div>
        </div>
      </footer>
    </section>

    <div v-if="providerDialogVisible" class="modal-mask" @click.self="providerDialogVisible = false">
      <div class="modal-card">
        <header class="modal-head">
          <h3>{{ providerDialogMode === 'create' ? '新增 Provider' : '编辑 Provider' }}</h3>
          <button class="close-btn" type="button" @click="providerDialogVisible = false">×</button>
        </header>

        <p v-if="providerError" class="error-banner">{{ providerError }}</p>

        <div class="form-grid two-column">
          <label class="field-block">
            <span>Provider 编码</span>
            <input v-model="providerForm.providerCode" class="field-control" type="text" :disabled="providerDialogMode === 'edit'" />
          </label>
          <label class="field-block">
            <span>Provider 名称</span>
            <input v-model="providerForm.providerName" class="field-control" type="text" />
          </label>
          <label class="field-block full-span">
            <span>基础地址</span>
            <input v-model="providerForm.baseUrl" class="field-control" type="text" placeholder="https://api.example.com/v1" />
          </label>
          <label class="field-block">
            <span>连接超时(ms)</span>
            <input v-model="providerForm.connectTimeoutMs" class="field-control" type="number" min="0" />
          </label>
          <label class="field-block">
            <span>读取超时(ms)</span>
            <input v-model="providerForm.readTimeoutMs" class="field-control" type="number" min="0" />
          </label>
          <label class="switch-block">
            <input v-model="providerForm.enabled" type="checkbox" />
            <span>启用 Provider</span>
          </label>
          <label class="field-block full-span">
            <span>备注</span>
            <textarea v-model="providerForm.remark" class="field-control textarea-control" rows="4" />
          </label>
        </div>

        <footer class="modal-actions">
          <button class="action-btn" type="button" @click="providerDialogVisible = false">取消</button>
          <button class="action-btn primary" type="button" :disabled="loading.providerSaving" @click="submitProviderForm">
            {{ loading.providerSaving ? '保存中...' : '保存' }}
          </button>
        </footer>
      </div>
    </div>

    <div v-if="modelDialogVisible" class="modal-mask" @click.self="modelDialogVisible = false">
      <div class="modal-card modal-large">
        <header class="modal-head">
          <h3>{{ modelDialogMode === 'create' ? '新增 Model' : '编辑 Model' }}</h3>
          <button class="close-btn" type="button" @click="modelDialogVisible = false">×</button>
        </header>

        <p v-if="modelError" class="error-banner">{{ modelError }}</p>

        <section class="dialog-section">
          <header class="section-head">
            <h4>模型基础配置</h4>
            <p>维护模型本体、Provider 绑定和调用参数。</p>
          </header>

          <div class="form-grid three-column">
            <label class="field-block">
              <span>模型编码</span>
              <input v-model="modelForm.modelCode" class="field-control" type="text" :disabled="modelDialogMode === 'edit'" />
            </label>
            <label class="field-block">
              <span>模型名称</span>
              <input v-model="modelForm.modelName" class="field-control" type="text" />
            </label>
            <label class="field-block">
              <span>所属 Provider</span>
              <select v-model="modelForm.providerCode" class="field-control">
                <option value="">请选择 Provider</option>
                <option v-for="item in providerOptions" :key="item.id" :value="item.providerCode">
                  {{ item.providerName }} ({{ item.providerCode }})
                </option>
              </select>
            </label>

            <label class="field-block">
              <span>Provider 模型标识</span>
              <input v-model="modelForm.apiModel" class="field-control" type="text" />
            </label>
            <label class="field-block">
              <span>能力标签</span>
              <input v-model="modelForm.capabilityTags" class="field-control" type="text" placeholder="chat,reasoning,vision" />
            </label>
            <label class="field-block">
              <span>优先级</span>
              <input v-model="modelForm.priority" class="field-control" type="number" min="0" />
            </label>

            <label class="field-block">
              <span>最大上下文 Token</span>
              <input v-model="modelForm.maxContextTokens" class="field-control" type="number" min="0" />
            </label>
            <label class="field-block">
              <span>最大输出 Token</span>
              <input v-model="modelForm.maxOutputTokens" class="field-control" type="number" min="0" />
            </label>
            <label class="field-block">
              <span>温度参数</span>
              <select v-model="modelForm.temperatureEnabled" class="field-control">
                <option :value="1">启用温度参数</option>
                <option :value="0">禁用温度参数</option>
              </select>
            </label>

            <label class="switch-block">
              <input v-model="modelForm.enabled" type="checkbox" />
              <span>启用 Model</span>
            </label>

            <label class="field-block full-span">
              <span>备注</span>
              <textarea v-model="modelForm.remark" class="field-control textarea-control" rows="3" />
            </label>
          </div>
        </section>

        <section class="dialog-section credential-section">
          <header class="section-head">
            <h4>内部凭证配置</h4>
            <p>凭证与 Model 同弹窗维护。编辑时留空 API Key 表示保持现值。</p>
          </header>

          <div class="form-grid three-column">
            <label class="field-block">
              <span>凭证编码</span>
              <input v-model="modelForm.credentialCode" class="field-control" type="text" />
            </label>
            <label class="field-block">
              <span>Key 版本</span>
              <input v-model="modelForm.keyVersion" class="field-control" type="number" min="1" />
            </label>
            <label class="switch-block">
              <input v-model="modelForm.credentialEnabled" type="checkbox" />
              <span>启用凭证</span>
            </label>

            <label class="field-block full-span">
              <span>API Key</span>
              <input
                v-model="modelForm.apiKeyInput"
                class="field-control"
                type="password"
                :placeholder="modelDialogMode === 'edit' ? '留空表示不修改现有 API Key' : '请输入 API Key'"
              />
            </label>
            <label class="field-block">
              <span>当前脱敏值</span>
              <input class="field-control" type="text" :value="modelForm.apiKeyMasked || '-'" disabled />
            </label>
            <label class="field-block">
              <span>过期时间</span>
              <input v-model="modelForm.expireAt" class="field-control" type="datetime-local" />
            </label>
            <label class="field-block full-span">
              <span>凭证备注</span>
              <textarea v-model="modelForm.credentialRemark" class="field-control textarea-control" rows="3" />
            </label>
          </div>
        </section>

        <footer class="modal-actions">
          <button class="action-btn" type="button" @click="modelDialogVisible = false">取消</button>
          <button class="action-btn primary" type="button" :disabled="loading.modelSaving" @click="submitModelForm">
            {{ loading.modelSaving ? '保存中...' : '保存' }}
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>
