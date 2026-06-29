<script setup>
import '../styles/ai.scss'
import { useAiPage } from '../service/ai'

const {
  activeTab,
  loading,
  modelFilters,
  kbFilters,
  modelList,
  kbList,
  providerOptions,
  modelDialogVisible,
  modelDialogMode,
  modelError,
  modelForm,
  kbDialogVisible,
  kbDialogMode,
  kbError,
  kbForm,
  enabledOptions,
  kbBizTypeOptions,
  kbStatusOptions,
  pageSizeOptions,
  currentPage,
  currentSize,
  pageSummary,
  totalPages,
  openModelEdit,
  openKbEdit,
  submitModelForm,
  submitKbForm,
  toggleModelStatus,
  toggleKbStatus,
  confirmDeleteModel,
  confirmDeleteKb,
  resetModelFilters,
  resetKbFilters,
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
        <p class="desc">页面统一维护 Model 与本地知识库主表配置，Provider 信息直接收口到模型配置。</p>
      </div>

      <button class="create-pill" type="button" @click="openCreateByTab">
        {{ activeTab === 'model' ? '新增 Model' : '新增 KB' }}
      </button>
    </header>

    <section class="workspace-card">
      <div class="tab-strip">
        <button class="tab-pill" :class="{ active: activeTab === 'model' }" type="button" @click="activeTab = 'model'">
          Model 管理
        </button>
        <button class="tab-pill" :class="{ active: activeTab === 'kb' }" type="button" @click="activeTab = 'kb'">Kb管理</button>
      </div>

      <div v-if="activeTab === 'model'" class="panel-shell">
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
                    <th>基础地址</th>
                    <th>API Model</th>
                    <th>能力标签</th>
                    <th>状态</th>
                    <th>优先级</th>
                    <th>脱敏 Key</th>
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
                    <td class="ellipsis">{{ row.baseUrl || '-' }}</td>
                    <td>{{ row.apiModel }}</td>
                    <td>
                      <div class="tag-list">
                        <span v-for="tag in tagList(row.capabilityTags)" :key="tag" class="soft-tag">{{ tag }}</span>
                        <span v-if="!tagList(row.capabilityTags).length">-</span>
                      </div>
                    </td>
                    <td>
                      <button class="status-btn" :class="row.enabled ? 'is-on' : 'is-off'" type="button" @click="toggleModelStatus(row)">
                        {{ row.enabled ? '启用' : '停用' }}
                      </button>
                    </td>
                    <td>{{ row.priority ?? '-' }}</td>
                    <td>{{ row.apiKeyMasked || '-' }}</td>
                    <td>{{ formatDateTime(row.updateTime) }}</td>
                    <td>
                      <div class="row-actions">
                        <button class="link-btn" type="button" @click="openModelEdit(row)">编辑</button>
                        <button class="link-btn danger" type="button" @click="confirmDeleteModel(row)">删除</button>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="!modelList.length">
                    <td colspan="11" class="empty-cell">暂无 Model 数据</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
        </div>
      </div>

      <div v-else class="panel-shell">
        <div class="toolbar-grid kb-toolbar">
          <input
            v-model="kbFilters.keyword"
            class="field-control"
            type="text"
            placeholder="搜索 KB 编码、名称、Provider KB"
            @keyup.enter="handleSearch"
          />

          <select v-model="kbFilters.status" class="field-control">
            <option v-for="item in kbStatusOptions" :key="item.label" :value="item.value">{{ item.label }}</option>
          </select>

          <select v-model="kbFilters.enabled" class="field-control">
            <option v-for="item in enabledOptions" :key="item.label" :value="item.value">{{ item.label }}</option>
          </select>

          <div class="toolbar-actions">
            <button class="action-btn primary" type="button" @click="handleSearch">查询</button>
            <button class="action-btn" type="button" @click="resetKbFilters">重置</button>
          </div>
        </div>

        <div class="table-card">
          <div v-if="loading.kb" class="table-state">正在加载 KB 列表...</div>

          <template v-else>
            <div class="table-scroll">
              <table class="data-table kb-table">
                <thead>
                  <tr>
                    <th>KB 编码</th>
                    <th>KB 名称</th>
                    <th>业务类型</th>
                    <th>Provider KB ID</th>
                    <th>状态</th>
                    <th>扩展信息</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in kbList" :key="row.id">
                    <td>{{ row.kbCode }}</td>
                    <td>{{ row.kbName }}</td>
                    <td>{{ row.bizType || '-' }}</td>
                    <td class="ellipsis">{{ row.providerKbId || '-' }}</td>
                    <td>{{ row.status || '-' }}</td>
                    <td class="ellipsis">{{ row.extJson ? JSON.stringify(row.extJson) : '-' }}</td>
                    <td>
                      <div class="row-actions">
                        <button class="status-btn" :class="row.enabled ? 'is-on' : 'is-off'" type="button" @click="toggleKbStatus(row)">
                          {{ row.enabled ? '停用' : '启用' }}
                        </button>
                        <button class="link-btn" type="button" @click="openKbEdit(row)">编辑</button>
                        <button class="link-btn danger" type="button" @click="confirmDeleteKb(row)">删除</button>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="!kbList.length">
                    <td colspan="7" class="empty-cell">暂无 KB 数据</td>
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
            <button class="action-btn" type="button" :disabled="currentPage >= totalPages" @click="handlePageChange(currentPage + 1)">
              下一页
            </button>
          </div>
        </div>
      </footer>
    </section>

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
              <span>Provider 编码</span>
              <input v-model="modelForm.providerCode" class="field-control" type="text" list="provider-code-options" />
            </label>

            <label class="field-block">
              <span>Provider 名称</span>
              <input v-model="modelForm.providerName" class="field-control" type="text" list="provider-name-options" />
            </label>

            <label class="field-block">
              <span>基础地址</span>
              <input v-model="modelForm.baseUrl" class="field-control" type="text" placeholder="https://api.example.com/v1" />
            </label>

            <label class="field-block">
              <span>Provider 模型标识</span>
              <input v-model="modelForm.apiModel" class="field-control" type="text" />
            </label>

            <label class="switch-block">
              <input v-model="modelForm.enabled" type="checkbox" />
              <span>启用 Model</span>
            </label>

            <label class="field-block full-span">
              <span>扩展配置 JSON</span>
              <textarea
                v-model="modelForm.extJson"
                class="field-control textarea-control code-textarea"
                rows="8"
                placeholder='{"maxContextTokens":32000,"maxOutputTokens":4096,"temperatureEnabled":1,"priority":100,"connectTimeoutMs":3000,"readTimeoutMs":30000}'
              />
            </label>
          </div>

          <datalist id="provider-code-options">
            <option v-for="item in providerOptions" :key="`code-${item.providerCode}`" :value="item.providerCode" />
          </datalist>
          <datalist id="provider-name-options">
            <option v-for="item in providerOptions" :key="`name-${item.providerCode}`" :value="item.providerName" />
          </datalist>
        </section>

        <section class="dialog-section credential-section">
          <header class="section-head">
            <h4>模型访问凭证</h4>
            <p>当前直接明文存储 API Key。编辑时留空表示保持现值。</p>
          </header>

          <div class="form-grid three-column">
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

    <div v-if="kbDialogVisible" class="modal-mask" @click.self="kbDialogVisible = false">
      <div class="modal-card modal-large">
        <header class="modal-head">
          <h3>{{ kbDialogMode === 'create' ? '新增 KB' : '编辑 KB' }}</h3>
          <button class="close-btn" type="button" @click="kbDialogVisible = false">×</button>
        </header>

        <p v-if="kbError" class="error-banner">{{ kbError }}</p>

        <div class="form-grid three-column">
          <label class="field-block">
            <span>KB 编码</span>
            <input v-model="kbForm.kbCode" class="field-control" type="text" :disabled="kbDialogMode === 'edit'" />
          </label>
          <label class="field-block">
            <span>KB 名称</span>
            <input v-model="kbForm.kbName" class="field-control" type="text" />
          </label>
          <label class="field-block">
            <span>业务类型</span>
            <select v-model="kbForm.bizType" class="field-control">
              <option v-for="item in kbBizTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>

          <label class="field-block">
            <span>Provider KB ID</span>
            <input v-model="kbForm.providerKbId" class="field-control" type="text" />
          </label>

          <label class="field-block">
            <span>状态</span>
            <select v-model="kbForm.status" class="field-control">
              <option v-for="item in kbStatusOptions.slice(1)" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>

          <label class="field-block full-span">
            <span>扩展信息 JSON</span>
            <textarea v-model="kbForm.extJson" class="field-control textarea-control" rows="4" placeholder='{"owner":"ai-engine"}' />
          </label>
        </div>

        <footer class="modal-actions">
          <button class="action-btn" type="button" @click="kbDialogVisible = false">取消</button>
          <button class="action-btn primary" type="button" :disabled="loading.kbSaving" @click="submitKbForm">
            {{ loading.kbSaving ? '保存中...' : '保存' }}
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>
