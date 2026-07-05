<script setup lang="ts">
import { ArrowLeft, Check, Plus } from '@element-plus/icons-vue'
import { computed, reactive } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const form = reactive({
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

const parsedTags = computed(() =>
  form.tags
    .split(/[\s,，]+/)
    .map((item) => item.trim())
    .filter(Boolean),
)

async function navigateBack() {
  await router.push('/settings/system/system-params')
}
</script>

<template>
  <div class="system-param-add-shell">
    <header class="system-param-add-hero">
      <div class="system-param-add-hero__left">
        <el-button plain @click="navigateBack">
          <el-icon><ArrowLeft /></el-icon>
          返回参数列表
        </el-button>
        <div>
          <h1>新增系统参数</h1>
          <p>创建新的系统配置项，后续可扩展到查看详情、编辑等独立页面。</p>
        </div>
      </div>
      <div class="system-param-add-hero__actions">
        <el-button plain>
          <el-icon><Plus /></el-icon>
          继续新增
        </el-button>
        <el-button type="primary">
          <el-icon><Check /></el-icon>
          保存参数
        </el-button>
      </div>
    </header>

    <section class="system-param-add-layout">
      <div class="system-param-add-card">
        <div class="system-param-add-card__title">核心参数</div>
        <el-form label-position="top" class="system-param-add-form">
          <el-form-item label="Key">
            <el-input v-model="form.key" placeholder="例如：app.theme.default" />
          </el-form-item>

          <el-form-item label="Type">
            <el-select v-model="form.type" placeholder="请选择参数类型">
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
              v-model="form.value"
              type="textarea"
              :rows="5"
              placeholder="请输入参数值"
            />
          </el-form-item>

          <el-form-item label="Desc">
            <el-input
              v-model="form.desc"
              type="textarea"
              :rows="3"
              placeholder="请输入参数说明"
            />
          </el-form-item>

          <el-form-item label="Enabled">
            <el-switch v-model="form.enabled" />
          </el-form-item>

          <el-form-item label="Tags">
            <el-input
              v-model="form.tags"
              placeholder="支持空格、英文逗号、中文逗号分隔"
            />
            <div class="system-param-add-form__hint">示例：界面配置 默认值 主题，核心参数</div>
            <div v-if="parsedTags.length" class="system-param-add-form__tags">
              <el-tag v-for="tag in parsedTags" :key="tag" size="small" effect="plain">
                {{ tag }}
              </el-tag>
            </div>
          </el-form-item>
        </el-form>
      </div>

      <div class="system-param-add-card system-param-add-card--side">
        <div class="system-param-add-card__title">预览摘要</div>
        <div class="system-param-preview">
          <div class="system-param-preview__item">
            <span>Key</span>
            <strong>{{ form.key || '-' }}</strong>
          </div>
          <div class="system-param-preview__item">
            <span>Type</span>
            <strong>{{ typeOptions.find((item) => item.value === form.type)?.label || '-' }}</strong>
          </div>
          <div class="system-param-preview__item">
            <span>Enabled</span>
            <strong>{{ form.enabled ? '已启用' : '未启用' }}</strong>
          </div>
          <div class="system-param-preview__item">
            <span>Tags</span>
            <div class="system-param-preview__tags">
              <el-tag v-for="tag in parsedTags" :key="tag" size="small" effect="plain">
                {{ tag }}
              </el-tag>
              <span v-if="!parsedTags.length">-</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.system-param-add-shell {
  min-height: 100vh;
  padding: 24px;
  background: #f4f6f8;
}

.system-param-add-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.system-param-add-hero__left {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.system-param-add-hero__left h1 {
  margin: 0 0 6px;
  color: #111827;
  font-size: 24px;
}

.system-param-add-hero__left p {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.6;
}

.system-param-add-hero__actions {
  display: flex;
  gap: 10px;
}

.system-param-add-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  margin-top: 18px;
}

.system-param-add-card {
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.system-param-add-card__title {
  margin-bottom: 14px;
  color: #111827;
  font-size: 15px;
  font-weight: 600;
}

.system-param-add-form {
  display: grid;
  gap: 6px;
}

.system-param-add-form :deep(.el-select) {
  width: 100%;
}

.system-param-add-form__hint {
  margin-top: 8px;
  color: #9ca3af;
  font-size: 12px;
}

.system-param-add-form__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.system-param-preview {
  display: grid;
  gap: 14px;
}

.system-param-preview__item {
  display: grid;
  gap: 6px;
}

.system-param-preview__item span {
  color: #9ca3af;
  font-size: 12px;
}

.system-param-preview__item strong {
  color: #111827;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-all;
}

.system-param-preview__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 960px) {
  .system-param-add-shell {
    padding: 16px;
  }

  .system-param-add-hero {
    flex-direction: column;
  }

  .system-param-add-hero__left {
    flex-direction: column;
    gap: 10px;
  }

  .system-param-add-hero__actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .system-param-add-layout {
    grid-template-columns: 1fr;
  }
}
</style>
