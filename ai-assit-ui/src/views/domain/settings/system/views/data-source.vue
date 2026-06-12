<script setup>
import { Connection, DataBoard, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import '../styles/data-source.css'
import { useDataSourcePage } from '../service/data-source'

const {
  keyword,
  selectedSourceKey,
  loading,
  errorMessage,
  dialogVisible,
  dialogMode,
  dialogError,
  saving,
  form,
  notice,
  sourceTypeOptions,
  syncModeOptions,
  statusOptions,
  authTypeOptions,
  filteredSources,
  openSource,
  statusClass,
  loadDataSources,
  openCreateDialog,
  openEditDialog,
  closeDialog,
  submitForm
} = useDataSourcePage()
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

    <div v-if="notice.text" :class="['notice-bar', notice.type === 'error' ? 'is-error' : 'is-success']">
      {{ notice.text }}
    </div>

    <section class="source-list-card">
      <div class="toolbar">
        <label class="search-box">
          <Search :size="16" />
          <input v-model="keyword" type="text" placeholder="搜索数据源名称、类型、负责人或库名" />
        </label>

        <div class="toolbar-actions">
          <button type="button" class="toolbar-secondary-btn" @click="loadDataSources">
            <RefreshRight :size="16" />
            刷新
          </button>
          <button type="button" class="toolbar-add-btn" @click="openCreateDialog">
            <Plus :size="16" />
            新增
          </button>
        </div>
      </div>

      <div class="source-stack">
        <div v-if="loading" class="placeholder-panel">
          <p>正在加载 `/dbEngine/api/v1/meta/data-source/_search` 的数据源列表...</p>
        </div>

        <div v-else-if="errorMessage" class="placeholder-panel is-error">
          <p>{{ errorMessage }}</p>
        </div>

        <div
          v-else
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
            <button type="button" class="row-action-btn" @click.stop="openEditDialog(item)">
              编辑
            </button>
          </div>
        </div>

        <div v-if="!loading && !errorMessage && !filteredSources.length" class="placeholder-panel">
          <p>没有匹配到数据源，当前列表已经直接读取 `meta` 模块对象。</p>
        </div>
      </div>
    </section>

    <div v-if="dialogVisible" class="modal-mask" @click.self="closeDialog">
      <div class="modal-card modal-large">
        <header class="modal-head">
          <h3>{{ dialogMode === 'create' ? '新增数据源' : '编辑数据源' }}</h3>
          <button class="close-btn" type="button" @click="closeDialog">×</button>
        </header>

        <p v-if="dialogError" class="error-banner">{{ dialogError }}</p>

        <div class="modal-body">
          <section class="dialog-section section-panel">
            <header class="section-head">
              <h4>基础信息</h4>
              <p>维护数据源编码、归属、状态和同步策略。</p>
            </header>

            <div class="form-grid two-column">
              <label class="field-block">
                <span>数据源 Key</span>
                <input v-model="form.sourceKey" class="field-control" type="text" :disabled="dialogMode === 'edit'" />
              </label>
              <label class="field-block">
                <span>数据源名称</span>
                <input v-model="form.sourceName" class="field-control" type="text" />
              </label>
              <label class="field-block">
                <span>数据源类型</span>
                <select v-model="form.sourceType" class="field-control">
                  <option v-for="item in sourceTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                </select>
              </label>
              <label class="field-block">
                <span>状态</span>
                <select v-model="form.status" class="field-control">
                  <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                </select>
              </label>

              <label class="field-block">
                <span>归属团队</span>
                <input v-model="form.ownerTeam" class="field-control" type="text" />
              </label>
              <label class="field-block">
                <span>负责人</span>
                <input v-model="form.ownerUser" class="field-control" type="text" />
              </label>
              <label class="field-block">
                <span>同步模式</span>
                <select v-model="form.syncMode" class="field-control">
                  <option v-for="item in syncModeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                </select>
              </label>
              <label class="field-block">
                <span>统一 Endpoint</span>
                <input v-model="form.endpoint" class="field-control" type="text" placeholder="jdbc:mysql://host:3306/db" />
              </label>

              <label class="switch-block full-span inline-switch">
                <input v-model="form.enabled" type="checkbox" />
                <span>启用数据源</span>
              </label>

              <label class="field-block full-span">
                <span>摘要说明</span>
                <textarea v-model="form.summary" class="field-control textarea-control" rows="3" />
              </label>
              <label class="field-block full-span">
                <span>备注</span>
                <textarea v-model="form.remark" class="field-control textarea-control" rows="3" />
              </label>
            </div>
          </section>

          <section class="dialog-section section-panel">
            <header class="section-head">
              <h4>数据库配置</h4>
              <p>当前列表页先按 `DATABASE` 作为主要维护场景接通。</p>
            </header>

            <div class="form-grid two-column">
              <label class="field-block">
                <span>数据库类型</span>
                <input v-model="form.dbType" class="field-control" type="text" placeholder="mysql / postgresql / clickhouse" />
              </label>
              <label class="field-block">
                <span>主机</span>
                <input v-model="form.host" class="field-control" type="text" />
              </label>
              <label class="field-block">
                <span>端口</span>
                <input v-model="form.port" class="field-control" type="number" min="0" />
              </label>
              <label class="field-block">
                <span>库名</span>
                <input v-model="form.databaseName" class="field-control" type="text" />
              </label>
              <label class="field-block">
                <span>Schema</span>
                <input v-model="form.schemaName" class="field-control" type="text" />
              </label>
              <label class="field-block full-span">
                <span>JDBC URL</span>
                <input v-model="form.jdbcUrl" class="field-control" type="text" />
              </label>
            </div>
          </section>

          <section class="dialog-section section-panel">
            <header class="section-head">
              <h4>认证与网络</h4>
              <p>认证密文字段保持后端对象结构，不在前端做额外封装。</p>
            </header>

            <div class="form-grid two-column">
              <label class="field-block">
                <span>认证类型</span>
                <select v-model="form.authType" class="field-control">
                  <option v-for="item in authTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                </select>
              </label>
              <label class="field-block">
                <span>用户名</span>
                <input v-model="form.username" class="field-control" type="text" />
              </label>
              <label class="field-block">
                <span>凭证引用</span>
                <input v-model="form.credentialRef" class="field-control" type="text" />
              </label>
              <label class="field-block">
                <span>Access Key</span>
                <input v-model="form.accessKey" class="field-control" type="text" />
              </label>

              <label class="field-block">
                <span>密码密文/引用</span>
                <input v-model="form.passwordCiphertext" class="field-control" type="text" />
              </label>
              <label class="field-block">
                <span>Token 密文/引用</span>
                <input v-model="form.tokenCiphertext" class="field-control" type="text" />
              </label>
              <label class="field-block full-span">
                <span>Secret Key 密文/引用</span>
                <input v-model="form.secretKeyCiphertext" class="field-control" type="text" />
              </label>

              <label class="field-block">
                <span>连接超时(ms)</span>
                <input v-model="form.connectTimeoutMs" class="field-control" type="number" min="0" />
              </label>
              <label class="field-block">
                <span>读取超时(ms)</span>
                <input v-model="form.readTimeoutMs" class="field-control" type="number" min="0" />
              </label>
              <label class="field-block full-span">
                <span>写入超时(ms)</span>
                <input v-model="form.writeTimeoutMs" class="field-control" type="number" min="0" />
              </label>
            </div>
          </section>
        </div>

        <footer class="modal-actions">
          <button class="action-btn" type="button" @click="closeDialog">取消</button>
          <button class="action-btn primary" type="button" :disabled="saving" @click="submitForm">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>
