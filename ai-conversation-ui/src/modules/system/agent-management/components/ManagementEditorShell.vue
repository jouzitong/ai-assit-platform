<script setup lang="ts">
import { ArrowLeft, Check, Promotion, Select } from '@element-plus/icons-vue'
import { computed } from 'vue'
import { LayoutPageHeader } from '../../../../components'
import type { ValidationReport } from '../types'

const props = withDefaults(defineProps<{
  title: string
  description?: string
  status?: string
  version?: number
  loading?: boolean
  saving?: boolean
  validating?: boolean
  publishing?: boolean
  saveDisabled?: boolean
  validateDisabled?: boolean
  publishDisabled?: boolean
  report?: ValidationReport | null
}>(), {
  description: '',
  status: 'DRAFT',
  version: 1,
  loading: false,
  saving: false,
  validating: false,
  publishing: false,
  saveDisabled: false,
  validateDisabled: false,
  publishDisabled: false,
  report: null,
})

const emit = defineEmits<{
  back: []
  save: []
  validate: []
  publish: []
}>()

const reportType = computed(() => props.report?.valid === false ? 'error' : 'success')
</script>

<template>
  <section v-loading="loading" class="management-editor">
    <LayoutPageHeader :title="title" :description="description">
      <template #leading>
        <el-button circle plain :icon="ArrowLeft" aria-label="返回列表" title="返回列表" @click="emit('back')" />
      </template>
      <template #actions>
        <div class="management-editor__actions">
          <el-tag effect="plain">{{ status || 'DRAFT' }} · v{{ version }}</el-tag>
          <el-button :icon="Check" :loading="saving" :disabled="saveDisabled" @click="emit('save')">保存草稿</el-button>
          <el-button :icon="Select" :loading="validating" :disabled="validateDisabled" @click="emit('validate')">校验</el-button>
          <el-button
            type="primary"
            :icon="Promotion"
            :loading="publishing"
            :disabled="publishDisabled"
            @click="emit('publish')"
          >
            发布
          </el-button>
        </div>
      </template>
    </LayoutPageHeader>

    <el-alert
      v-if="report"
      :type="reportType"
      :closable="false"
      show-icon
      :title="report.valid === false ? '定义校验未通过' : '定义校验通过'"
    >
      <template #default>
        <div v-if="report.message">{{ report.message }}</div>
        <ul v-if="report.issues?.length || report.warnings?.length" class="management-editor__issues">
          <li v-for="issue in [...(report.issues || []), ...(report.warnings || [])]" :key="`${issue.code}-${issue.path}-${issue.message}`">
            <strong>{{ issue.severity || 'INFO' }}</strong>
            <span v-if="issue.path">{{ issue.path }}：</span>{{ issue.message }}
          </li>
        </ul>
      </template>
    </el-alert>

    <div class="management-editor__body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.management-editor {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--app-space-4);
  min-width: 0;
  min-height: 0;
  padding: var(--app-space-5);
  border: 1px solid var(--system-border);
  border-radius: var(--app-radius-xl);
  background: var(--system-surface-strong);
  box-shadow: var(--system-shadow);
  container-type: inline-size;
}

.management-editor__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--app-space-2);
}

.management-editor__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.management-editor__issues {
  margin: var(--app-space-2) 0 0;
  padding-left: var(--app-space-5);
}

@container (max-width: 720px) {
  .management-editor__actions {
    justify-content: flex-start;
  }
}
</style>
