<script setup lang="ts">
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { AppCodeEditor, LayoutFormGrid, LayoutFormGridItem } from '../../../../components'
import {
  createWorkflow,
  getWorkflow,
  publishWorkflow,
  testWorkflow,
  updateWorkflow,
  validateWorkflow,
} from '../api/workflows'
import DefinitionTestRunPanel from '../components/DefinitionTestRunPanel.vue'
import ManagementEditorShell from '../components/ManagementEditorShell.vue'
import { useDefinitionEditor } from '../composables/useDefinitionEditor'
import type { ArtifactWorkflowDefinition, WorkflowCheck } from '../types'

type ArtifactDraft = {
  code: string
  name: string
  artifactType: string
  contentFormat: string
  required: boolean
  visible: boolean
  schemaRef: string
  templateRef: string
  inlineSchemaText: string
  inlineTemplate: string
}

type CheckDraft = WorkflowCheck & { configText: string }

const formRef = ref<FormInstance>()
const form = reactive({
  code: '', name: '', description: '', enabled: true,
  artifacts: [] as ArtifactDraft[],
  checks: [] as CheckDraft[],
  requireAllRequiredArtifacts: true,
  requireAllBlockingChecksPassed: true,
  maxRepairAttempts: 2,
  onExhausted: 'INPUT_REQUIRED',
})

const rules: FormRules = {
  code: [
    { required: true, message: '请输入 Workflow 编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9-]{1,63}$/, message: '使用小写字母、数字和短横线', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入 Workflow 名称', trigger: 'blur' }],
}

function reset() {
  Object.assign(form, {
    code: '', name: '', description: '', enabled: true, artifacts: [], checks: [],
    requireAllRequiredArtifacts: true, requireAllBlockingChecksPassed: true,
    maxRepairAttempts: 2, onExhausted: 'INPUT_REQUIRED',
  })
  addArtifact()
}

function apply(value: ArtifactWorkflowDefinition) {
  const spec = value.spec || value
  form.code = value.code || value.metadata?.code || ''
  form.name = value.name || value.metadata?.name || ''
  form.description = value.description || value.metadata?.description || ''
  form.enabled = value.enabled !== false
  form.artifacts = (spec.artifacts || []).map(item => ({
    code: item.code, name: item.name || '', artifactType: item.artifactType || 'TEXT',
    contentFormat: item.contentFormat || 'MARKDOWN', required: item.required !== false,
    visible: item.visible !== false, schemaRef: item.schemaRef || '', templateRef: item.templateRef || '',
    inlineSchemaText: item.inlineSchema ? JSON.stringify(item.inlineSchema, null, 2) : '',
    inlineTemplate: item.inlineTemplate || '',
  }))
  form.checks = (spec.checks || []).map(item => ({
    ...item,
    configText: item.config ? JSON.stringify(item.config, null, 2) : '',
  }))
  form.requireAllRequiredArtifacts = spec.completionPolicy?.requireAllRequiredArtifacts !== false
  form.requireAllBlockingChecksPassed = spec.completionPolicy?.requireAllBlockingChecksPassed !== false
  form.maxRepairAttempts = Number(spec.repairPolicy?.maxRepairAttempts ?? 2)
  form.onExhausted = spec.repairPolicy?.onExhausted || 'INPUT_REQUIRED'
}

function addArtifact() {
  form.artifacts.push({
    code: '', name: '', artifactType: 'TEXT', contentFormat: 'MARKDOWN', required: true, visible: true,
    schemaRef: '', templateRef: '', inlineSchemaText: '', inlineTemplate: '',
  })
}

function addCheck() {
  form.checks.push({
    code: '', name: '', targetArtifact: form.artifacts[0]?.code || '', checkerType: 'JSON_SCHEMA', checkerRef: '',
    severity: 'ERROR', blocking: true, retryable: true, configText: '',
  })
}

function parseObject(text: string, label: string) {
  if (!text.trim()) return undefined
  try {
    const value = JSON.parse(text)
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error()
    return value as Record<string, unknown>
  }
  catch {
    ElMessage.error(`${label}必须是合法 JSON Object`)
    return null
  }
}

function getPayload(): ArtifactWorkflowDefinition | null {
  if (!form.code.trim() || !form.name.trim()) {
    ElMessage.error('请填写 Workflow 编码和名称')
    return null
  }
  if (!form.artifacts.length || form.artifacts.some(item => !item.code.trim() || !item.artifactType)) {
    ElMessage.error('至少配置一个具有编码和类型的 Artifact')
    return null
  }
  if (new Set(form.artifacts.map(item => item.code.trim())).size !== form.artifacts.length) {
    ElMessage.error('Artifact 编码不能重复')
    return null
  }

  const artifacts = []
  for (const item of form.artifacts) {
    const inlineSchema = parseObject(item.inlineSchemaText, `Artifact ${item.code} 的 Schema`)
    if (inlineSchema === null) return null
    artifacts.push({
      code: item.code.trim(), name: item.name.trim(), artifactType: item.artifactType,
      contentFormat: item.contentFormat, required: item.required, visible: item.visible,
      schemaRef: item.schemaRef.trim() || undefined, templateRef: item.templateRef.trim() || undefined,
      inlineSchema: inlineSchema || undefined, inlineTemplate: item.inlineTemplate || undefined,
    })
  }

  const checks: WorkflowCheck[] = []
  if (new Set(form.checks.map(item => item.code.trim())).size !== form.checks.length) {
    ElMessage.error('Check 编码不能重复')
    return null
  }
  for (const item of form.checks) {
    if (!item.code.trim() || !item.targetArtifact || !form.artifacts.some(artifact => artifact.code.trim() === item.targetArtifact)) {
      ElMessage.error('Check 必须填写编码和有效的目标 Artifact')
      return null
    }
    if (item.checkerType !== 'JSON_SCHEMA' && !item.checkerRef?.trim()) {
      ElMessage.error(`Check ${item.code} 必须填写检查器引用`)
      return null
    }
    const config = parseObject(item.configText, `Check ${item.code} 的配置`)
    if (config === null) return null
    checks.push({
      code: item.code.trim(), name: item.name?.trim(), targetArtifact: item.targetArtifact,
      checkerType: item.checkerType, checkerRef: item.checkerRef?.trim() || undefined,
      severity: item.severity, blocking: item.blocking, retryable: item.retryable, config: config || undefined,
    })
  }

  return {
    code: form.code.trim(), name: form.name.trim(), description: form.description.trim(), enabled: form.enabled,
    status: editor.saved.value?.status || 'DRAFT', draftVersion: editor.saved.value?.draftVersion || editor.currentVersion.value,
    apiVersion: 'ai.platform/v1alpha1',
    kind: 'ArtifactWorkflow',
    metadata: {
      code: form.code.trim(),
      version: editor.currentVersion.value,
      name: form.name.trim(),
      description: form.description.trim(),
    },
    spec: {
      artifacts,
      checks,
      completionPolicy: {
        requireAllRequiredArtifacts: form.requireAllRequiredArtifacts,
        requireAllBlockingChecksPassed: form.requireAllBlockingChecksPassed,
      },
      repairPolicy: { maxRepairAttempts: form.maxRepairAttempts, onExhausted: form.onExhausted },
    },
  }
}

const editor = useDefinitionEditor<ArtifactWorkflowDefinition>({
  routeBase: '/settings/system/workflows', label: 'Workflow', get: getWorkflow,
  create: createWorkflow, update: updateWorkflow, validate: validateWorkflow, publish: publishWorkflow,
  getPayload, apply, reset,
})

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) return
  await editor.save()
}

async function runAcceptanceTest(payload: Record<string, unknown>) {
  const definition = await editor.save()
  if (!definition) throw new Error('Workflow 草稿保存失败，未执行验收测试')
  return testWorkflow(editor.currentCode.value, editor.currentVersion.value, payload)
}

onMounted(editor.load)
</script>

<template>
  <ManagementEditorShell
    :title="editor.isCreate.value ? '新增 Artifact Workflow' : `编辑 Workflow · ${form.name || form.code}`"
    description="Workflow 定义产出物契约和验收规则，由 Agent 根据目标自主规划执行。"
    :status="editor.status.value" :version="editor.currentVersion.value" :loading="editor.loading.value"
    :saving="editor.saving.value" :validating="editor.validating.value" :publishing="editor.publishing.value"
    :report="editor.report.value" @back="editor.back" @save="save" @validate="editor.validate" @publish="editor.publish"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" status-icon>
      <el-tabs>
        <el-tab-pane label="基础信息">
          <LayoutFormGrid :columns="2">
            <LayoutFormGridItem><el-form-item label="Workflow 编码" prop="code"><el-input v-model="form.code" :disabled="!editor.isCreate.value" placeholder="home-chat-output" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem span="full"><el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="启用状态"><el-switch v-model="form.enabled" inline-prompt active-text="启用" inactive-text="停用" /></el-form-item></LayoutFormGridItem>
          </LayoutFormGrid>
        </el-tab-pane>

        <el-tab-pane label="Artifacts">
          <div class="workflow-editor__toolbar"><el-button :icon="Plus" @click="addArtifact">添加 Artifact</el-button></div>
          <el-collapse>
            <el-collapse-item v-for="(artifact, index) in form.artifacts" :key="index" :name="index">
              <template #title><strong>{{ artifact.code || `Artifact ${index + 1}` }}</strong><el-tag effect="plain">{{ artifact.artifactType }}</el-tag></template>
              <div class="workflow-editor__item-actions"><el-button link type="danger" :icon="Delete" :disabled="form.artifacts.length === 1" @click.stop="form.artifacts.splice(index, 1)">删除</el-button></div>
              <LayoutFormGrid :columns="2">
                <LayoutFormGridItem><el-form-item label="Artifact 编码" required><el-input v-model="artifact.code" placeholder="final-answer" /></el-form-item></LayoutFormGridItem>
                <LayoutFormGridItem><el-form-item label="名称"><el-input v-model="artifact.name" /></el-form-item></LayoutFormGridItem>
                <LayoutFormGridItem><el-form-item label="类型"><el-select v-model="artifact.artifactType"><el-option label="TEXT" value="TEXT" /><el-option label="JSON" value="JSON" /><el-option label="RENDER_JSON" value="RENDER_JSON" /><el-option label="FILE" value="FILE" /></el-select></el-form-item></LayoutFormGridItem>
                <LayoutFormGridItem><el-form-item label="内容格式"><el-select v-model="artifact.contentFormat" allow-create filterable><el-option label="MARKDOWN" value="MARKDOWN" /><el-option label="JSON" value="JSON" /><el-option label="TEXT" value="TEXT" /></el-select></el-form-item></LayoutFormGridItem>
                <LayoutFormGridItem><el-form-item label="Schema 引用"><el-input v-model="artifact.schemaRef" placeholder="schema://render-document/v2" /></el-form-item></LayoutFormGridItem>
                <LayoutFormGridItem><el-form-item label="模板引用"><el-input v-model="artifact.templateRef" placeholder="template://report/v1" /></el-form-item></LayoutFormGridItem>
                <LayoutFormGridItem><el-form-item label="必需"><el-switch v-model="artifact.required" /></el-form-item></LayoutFormGridItem>
                <LayoutFormGridItem><el-form-item label="对用户可见"><el-switch v-model="artifact.visible" /></el-form-item></LayoutFormGridItem>
                <LayoutFormGridItem><el-form-item label="内联 JSON Schema"><AppCodeEditor v-model="artifact.inlineSchemaText" format="json" min-height="180px" :max-rows="12" /></el-form-item></LayoutFormGridItem>
                <LayoutFormGridItem><el-form-item label="内联模板"><AppCodeEditor v-model="artifact.inlineTemplate" format="markdown" min-height="180px" :max-rows="12" /></el-form-item></LayoutFormGridItem>
              </LayoutFormGrid>
            </el-collapse-item>
          </el-collapse>
        </el-tab-pane>

        <el-tab-pane label="Checks">
          <div class="workflow-editor__toolbar"><el-button :icon="Plus" @click="addCheck">添加 Check</el-button></div>
          <el-table :data="form.checks" border empty-text="尚未配置检查规则">
            <el-table-column type="expand" width="52">
              <template #default="{ row }">
                <div class="workflow-editor__check-config">
                  <el-form-item label="Check Config JSON">
                    <AppCodeEditor v-model="row.configText" format="json" min-height="180px" :max-rows="12" />
                  </el-form-item>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="编码" min-width="150"><template #default="{ row }"><el-input v-model="row.code" /></template></el-table-column>
            <el-table-column label="名称" min-width="170"><template #default="{ row }"><el-input v-model="row.name" /></template></el-table-column>
            <el-table-column label="目标 Artifact" min-width="170"><template #default="{ row }"><el-select v-model="row.targetArtifact"><el-option v-for="artifact in form.artifacts" :key="artifact.code" :label="artifact.name || artifact.code" :value="artifact.code" /></el-select></template></el-table-column>
            <el-table-column label="检查器" min-width="145"><template #default="{ row }"><el-select v-model="row.checkerType"><el-option label="JSON Schema" value="JSON_SCHEMA" /><el-option label="Tool" value="TOOL" /><el-option label="Agent" value="AGENT" /></el-select></template></el-table-column>
            <el-table-column label="检查器引用" min-width="210"><template #default="{ row }"><el-input v-model="row.checkerRef" :disabled="row.checkerType === 'JSON_SCHEMA'" /></template></el-table-column>
            <el-table-column label="级别" width="130"><template #default="{ row }"><el-select v-model="row.severity"><el-option label="ERROR" value="ERROR" /><el-option label="WARNING" value="WARNING" /><el-option label="INFO" value="INFO" /></el-select></template></el-table-column>
            <el-table-column label="阻断" width="80" align="center"><template #default="{ row }"><el-switch v-model="row.blocking" /></template></el-table-column>
            <el-table-column label="可修复" width="80" align="center"><template #default="{ row }"><el-switch v-model="row.retryable" /></template></el-table-column>
            <el-table-column label="操作" width="72" align="center"><template #default="{ $index }"><el-button link type="danger" :icon="Delete" @click="form.checks.splice($index, 1)" /></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="完成与修复策略">
          <LayoutFormGrid :columns="2">
            <LayoutFormGridItem><el-form-item label="所有必需 Artifact 必须存在"><el-switch v-model="form.requireAllRequiredArtifacts" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="所有阻断 Check 必须通过"><el-switch v-model="form.requireAllBlockingChecksPassed" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="最大修复次数"><el-input-number v-model="form.maxRepairAttempts" :min="0" :max="10" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="修复耗尽后"><el-select v-model="form.onExhausted"><el-option label="请求用户输入" value="INPUT_REQUIRED" /><el-option label="任务失败" value="FAILED" /></el-select></el-form-item></LayoutFormGridItem>
          </LayoutFormGrid>
        </el-tab-pane>

        <el-tab-pane label="验收测试">
          <DefinitionTestRunPanel
            :execute="runAcceptanceTest"
            :disabled="editor.isCreate.value || !editor.currentCode.value"
            input-hint="输入待验收的 artifacts 和上下文，执行当前 Workflow 的 Artifact Contract 与 Check。"
          />
        </el-tab-pane>
      </el-tabs>
    </el-form>
  </ManagementEditorShell>
</template>

<style scoped>
.workflow-editor__toolbar,
.workflow-editor__item-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--app-space-3);
}

.workflow-editor__check-config {
  padding: var(--app-space-3) var(--app-space-5);
}

:deep(.el-collapse-item__title) {
  gap: var(--app-space-3);
}

:deep(.el-select),
:deep(.el-input-number) {
  width: 100%;
}
</style>
