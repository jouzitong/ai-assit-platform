<script setup lang="ts">
import { Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { computed, reactive, ref } from 'vue'

const keyword = ref('')
const pageSize = ref(20)
const currentPage = ref(1)
const addDialogVisible = ref(false)

const pageSizeOptions = [10, 20, 50, 100, 200, 500]
const addForm = reactive({
  key: '',
  value: '',
  type: 'string',
  desc: '',
  enabled: true,
  tags: '',
})
const typeOptions = [
  { label: '字符串', value: 'string' },
  { label: '数字', value: 'number' },
  { label: '布尔值', value: 'boolean' },
  { label: 'JSON', value: 'json' },
  { label: '富文本', value: 'rich-text' },
]

const parameterRecords = [
  {
    key: 'app.theme.default',
    desc: '控制系统默认主题，影响首次进入时的界面风格。',
    value: 'light',
    tags: ['界面配置', '默认值'],
    enabled: true,
  },
  {
    key: 'app.trace.enabled',
    desc: '控制是否开启全链路追踪日志，用于排查跨服务调用问题。',
    value: 'true',
    tags: ['系统能力', '日志'],
    enabled: true,
  },
  {
    key: 'chat.default.model',
    desc: '指定聊天模块默认模型，未显式选择模型时会自动使用该参数。',
    value: 'qwen3.6-plus',
    tags: ['AI 配置', '模型'],
    enabled: true,
  },
  {
    key: 'render.cache.ttl',
    desc: '控制渲染缓存有效期，单位秒，用于页面二次加载时的性能优化。',
    value: '300',
    tags: ['渲染配置', '缓存'],
    enabled: false,
  },
  {
    key: 'system.dashboard.notice',
    desc: '系统首页公告文案配置，用于展示版本提示、维护说明或运营通知。',
    value: '当前系统将于周五晚间进行配置发布，请提前完成数据源校验并确认组件版本一致性。',
    tags: ['运营配置', '公告'],
    enabled: true,
  },
]

const filteredParameterRecords = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase()
  if (!normalizedKeyword) {
    return parameterRecords
  }

  return parameterRecords.filter((record) =>
    record.key.toLowerCase().includes(normalizedKeyword)
    || record.desc.toLowerCase().includes(normalizedKeyword)
    || record.tags.some((tag) => tag.toLowerCase().includes(normalizedKeyword)),
  )
})
const parsedTags = computed(() =>
  addForm.tags
    .split(/[\s,，]+/)
    .map((item) => item.trim())
    .filter(Boolean),
)

function resetAddForm() {
  addForm.key = ''
  addForm.value = ''
  addForm.type = 'string'
  addForm.desc = ''
  addForm.enabled = true
  addForm.tags = ''
}

function closeAddDialog() {
  addDialogVisible.value = false
}
</script>

<template>
  <section class="system-params-page">
    <el-container class="system-params-layout">
      <el-header class="system-params-layout__header">
        <div class="system-params-layout__title">
          <h3>系统参数</h3>
          <p>维护系统级参数、默认开关和全局配置。</p>
        </div>
        <div class="system-params-layout__tools">
          <el-input v-model="keyword" placeholder="搜索参数名称 / 参数组 / 参数键" clearable>
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button plain>
            <el-icon><RefreshRight /></el-icon>
            刷新
          </el-button>
          <el-button type="primary" @click="addDialogVisible = true">
            <el-icon><Plus /></el-icon>
            新增参数
          </el-button>
        </div>
      </el-header>

      <el-main class="system-params-layout__main">
        <div
          v-for="record in filteredParameterRecords"
          :key="record.key"
          class="system-params-card"
        >
          <div class="system-params-card__head">
            <div class="system-params-card__key">{{ record.key }}</div>
            <el-switch :model-value="record.enabled" size="small" />
          </div>

          <div class="system-params-card__desc">{{ record.desc }}</div>

          <div class="system-params-card__value" :title="record.value">
            {{ record.value }}
          </div>

          <div class="system-params-card__footer">
            <div class="system-params-card__tags">
              <el-tag
                v-for="tag in record.tags"
                :key="tag"
                size="small"
                effect="plain"
              >
                {{ tag }}
              </el-tag>
            </div>
            <span :class="['system-params-card__enabled', { 'is-off': !record.enabled }]">
              {{ record.enabled ? '已启用' : '未启用' }}
            </span>
          </div>
        </div>
      </el-main>

      <el-footer class="system-params-layout__footer">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="pageSizeOptions"
          :pager-count="5"
          layout="total, sizes, prev, pager, next"
          :total="filteredParameterRecords.length"
        />
      </el-footer>
    </el-container>

    <el-dialog
      v-model="addDialogVisible"
      title="新增系统参数"
      width="680"
      draggable
      overflow
      destroy-on-close
      @closed="resetAddForm"
    >
      <div class="system-params-dialog">
        <el-form label-position="top" class="system-params-dialog__form">
          <el-form-item label="Key">
            <el-input v-model="addForm.key" placeholder="例如：app.theme.default" />
          </el-form-item>

          <el-form-item label="Type">
            <el-select v-model="addForm.type" placeholder="请选择参数类型">
              <el-option
                v-for="option in typeOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="Value">
            <el-input
              v-model="addForm.value"
              type="textarea"
              :rows="5"
              placeholder="请输入参数值"
            />
          </el-form-item>

          <el-form-item label="Desc">
            <el-input
              v-model="addForm.desc"
              type="textarea"
              :rows="3"
              placeholder="请输入参数说明"
            />
          </el-form-item>

          <div class="system-params-dialog__grid">
            <el-form-item label="Enabled">
              <el-switch v-model="addForm.enabled" />
            </el-form-item>

            <el-form-item label="Tags">
              <el-input
                v-model="addForm.tags"
                placeholder="支持空格、英文逗号、中文逗号分隔"
              />
            </el-form-item>
          </div>

          <div v-if="parsedTags.length" class="system-params-dialog__tags">
            <el-tag v-for="tag in parsedTags" :key="tag" size="small" effect="plain">
              {{ tag }}
            </el-tag>
          </div>
        </el-form>
      </div>

      <template #footer>
        <div class="system-params-dialog__footer">
          <el-button @click="closeAddDialog">取消</el-button>
          <el-button type="primary" @click="closeAddDialog">确认</el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.system-params-page {
  display: flex;
  flex: 1;
  min-height: 0;
}

.system-params-layout {
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.system-params-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 66px;
  padding: 0 16px;
  border-bottom: 1px solid #eef2f7;
}

.system-params-layout__title h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
}

.system-params-layout__title p {
  margin: 3px 0 0;
  color: #6b7280;
  font-size: 12px;
}

.system-params-layout__tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.system-params-layout__tools :deep(.el-input) {
  width: 260px;
}

.system-params-layout__main {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 320px));
  align-content: start;
  justify-content: start;
  gap: 14px;
  min-height: 0;
  padding: 14px 16px;
  background: #f8fafc;
  overflow-y: auto;
}

.system-params-card {
  display: grid;
  gap: 12px;
  max-width: 320px;
  padding: 16px;
  border: 1px solid #e8edf3;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.04);
}

.system-params-card__head,
.system-params-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.system-params-card__key {
  color: #111827;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  word-break: break-all;
}

.system-params-card__desc,
.system-params-card__enabled {
  color: #6b7280;
  font-size: 12px;
  line-height: 1.6;
}

.system-params-card__value {
  overflow: hidden;
  color: #0f172a;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.system-params-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.system-params-card__enabled.is-off {
  color: #9ca3af;
}

.system-params-layout__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: 44px;
  padding: 0 16px;
  border-top: 1px solid #eef2f7;
  background: #fff;
}

.system-params-dialog__form :deep(.el-select) {
  width: 100%;
}

.system-params-dialog__grid {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 12px;
}

.system-params-dialog__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}

.system-params-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 960px) {
  .system-params-layout__header {
    flex-direction: column;
    align-items: flex-start;
    height: auto;
    padding: 12px;
  }

  .system-params-layout__tools {
    width: 100%;
    flex-wrap: wrap;
  }

  .system-params-layout__tools :deep(.el-input) {
    width: 100%;
  }

  .system-params-dialog__grid {
    grid-template-columns: 1fr;
  }

  .system-params-layout__main {
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  }

  .system-params-card {
    max-width: none;
  }
}
</style>
