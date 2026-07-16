<script setup lang="ts">
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { AppCodeEditor, LayoutFormGrid, LayoutFormGridItem } from '../../../../components'
import {
  createAgent,
  getAgent,
  getAgentCompatibility,
  publishAgent,
  testAgent,
  updateAgent,
  validateAgent,
} from '../api/agents'
import DefinitionTestRunPanel from '../components/DefinitionTestRunPanel.vue'
import ManagementEditorShell from '../components/ManagementEditorShell.vue'
import { useDefinitionEditor } from '../composables/useDefinitionEditor'
import type { AgentCollaborationRef, AgentDefinition, AgentManifestSpec, DefinitionRef } from '../types'

const formRef = ref<FormInstance>()
const compatibilityLoading = ref(false)
const compatibilitySummary = ref('')

const form = reactive({
  code: '',
  name: '',
  description: '',
  enabled: true,
  labelsText: '{}',
  instructions: '',
  modelRef: '',
  modelSettingsText: '{}',
  outputMode: 'text' as 'text' | 'jsonSchema' | 'artifactSet',
  workflowRef: '',
  outputSchemaText: '',
  toolRefs: [] as string[],
  skillRefs: [] as string[],
  knowledgeRefs: [] as string[],
  mcpRefs: [] as string[],
  inputGuardrailRefs: [] as string[],
  outputGuardrailRefs: [] as string[],
  collaborators: [] as AgentCollaborationRef[],
  maxTurns: 12,
  timeoutMs: 120000,
  maxAgentDepth: 4,
  toolConcurrency: 1,
  stateStrategy: 'applicationReplay',
  tracingEnabled: true,
  workflowName: '',
})

const rules: FormRules = {
  code: [
    { required: true, message: '请输入 Agent 编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9-]{1,63}$/, message: '使用小写字母、数字和短横线，长度 2-64', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入 Agent 名称', trigger: 'blur' }],
  instructions: [{ required: true, message: '请输入 Agent Instructions', trigger: 'blur' }],
  modelRef: [{ required: true, message: '请输入 model:// 引用', trigger: 'blur' }],
}

function reset() {
  Object.assign(form, {
    code: '', name: '', description: '', enabled: true, labelsText: '{}', instructions: '', modelRef: '',
    modelSettingsText: '{}', outputMode: 'text', workflowRef: '', outputSchemaText: '', toolRefs: [], skillRefs: [],
    knowledgeRefs: [], mcpRefs: [], inputGuardrailRefs: [], outputGuardrailRefs: [], collaborators: [], maxTurns: 12,
    timeoutMs: 120000, maxAgentDepth: 4, toolConcurrency: 1, stateStrategy: 'applicationReplay',
    tracingEnabled: true, workflowName: '',
  })
  compatibilitySummary.value = ''
}

function refsToStrings(values?: DefinitionRef[]) {
  return (values || []).map(item => item.ref).filter(Boolean)
}

function apply(value: AgentDefinition) {
  const manifest = value.manifest
  const spec = value.spec || manifest?.spec || {}
  const runtime = spec.runtimeDefaults || {}
  const metadata = value.metadata || manifest?.metadata
  form.code = value.code || metadata?.code || ''
  form.name = value.name || metadata?.name || ''
  form.description = value.description || metadata?.description || ''
  form.enabled = value.enabled !== false
  form.labelsText = JSON.stringify(value.labels || metadata?.labels || {}, null, 2)
  form.instructions = spec.instructions?.text || ''
  form.modelRef = spec.model?.ref || ''
  form.modelSettingsText = JSON.stringify(spec.model?.settings || {}, null, 2)
  form.outputMode = spec.output?.mode || 'text'
  form.workflowRef = spec.output?.workflowRef || ''
  form.outputSchemaText = spec.output?.schema ? JSON.stringify(spec.output.schema, null, 2) : ''
  form.toolRefs = refsToStrings(spec.toolRefs)
  form.skillRefs = refsToStrings(spec.skillRefs)
  form.knowledgeRefs = refsToStrings(spec.knowledgeRefs)
  form.mcpRefs = refsToStrings(spec.mcpRefs)
  form.inputGuardrailRefs = refsToStrings(spec.guardrails?.input)
  form.outputGuardrailRefs = refsToStrings(spec.guardrails?.output)
  form.collaborators = [
    ...(spec.collaboration?.agentTools || []).map(item => ({ ...item, mode: 'AS_TOOL' as const })),
    ...(spec.collaboration?.handoffs || []).map(item => ({ ...item, mode: 'HANDOFF' as const })),
  ]
  form.maxTurns = Number(runtime.maxTurns || 12)
  form.timeoutMs = Number(runtime.timeoutMs || 120000)
  form.maxAgentDepth = Number(runtime.maxAgentDepth || 4)
  form.toolConcurrency = Number(runtime.toolConcurrency || 1)
  form.stateStrategy = runtime.stateStrategy || 'applicationReplay'
  form.tracingEnabled = runtime.tracing?.enabled !== false
  form.workflowName = runtime.tracing?.workflowName || form.code
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

function refItems(values: string[]): DefinitionRef[] {
  return values.map(value => ({ ref: value.trim(), enabled: true })).filter(item => item.ref)
}

function getPayload(): AgentDefinition | null {
  if (!form.code.trim() || !form.name.trim() || !form.instructions.trim() || !form.modelRef.trim()) {
    ElMessage.error('请完整填写编码、名称、Instructions 和模型引用')
    return null
  }
  if (!form.modelRef.trim().startsWith('model://')) {
    ElMessage.error('模型引用必须使用 model:// URI')
    return null
  }
  const capabilityGroups = [
    ['Tool', 'tool://', form.toolRefs],
    ['Skill', 'skill://', form.skillRefs],
    ['Knowledge', 'knowledge://', form.knowledgeRefs],
    ['MCP', 'mcp://', form.mcpRefs],
    ['输入 Guardrail', 'guardrail://', form.inputGuardrailRefs],
    ['输出 Guardrail', 'guardrail://', form.outputGuardrailRefs],
  ] as const
  for (const [label, prefix, values] of capabilityGroups) {
    if (values.some(value => !value.trim().startsWith(prefix))) {
      ElMessage.error(`${label} 引用必须使用 ${prefix} URI`)
      return null
    }
  }
  if (form.collaborators.some(item => !item.targetAgentRef.trim().startsWith('agent://'))) {
    ElMessage.error('协作 Agent 必须使用 agent:// URI')
    return null
  }
  if (form.collaborators.some(item => item.mode === 'AS_TOOL' && !item.toolName?.trim())) {
    ElMessage.error('Agent as Tool 必须填写 Tool 名称')
    return null
  }
  if (form.outputMode === 'artifactSet' && !form.workflowRef.trim().startsWith('workflow://')) {
    ElMessage.error('Artifact Set 输出必须引用 workflow:// 定义')
    return null
  }
  if (form.outputMode === 'jsonSchema' && !form.outputSchemaText.trim()) {
    ElMessage.error('JSON Schema 输出必须填写输出 Schema')
    return null
  }
  const labels = parseObject(form.labelsText, 'Labels')
  const modelSettings = parseObject(form.modelSettingsText, '模型设置')
  const outputSchema = parseObject(form.outputSchemaText, '输出 Schema')
  if (labels === null || modelSettings === null || outputSchema === null) return null

  const agentTools = form.collaborators.filter(item => item.mode === 'AS_TOOL')
  const handoffs = form.collaborators.filter(item => item.mode === 'HANDOFF')
  const spec: AgentManifestSpec = {
    instructions: { type: 'inline', text: form.instructions },
    model: { ref: form.modelRef.trim(), settings: modelSettings },
    output: {
      mode: form.outputMode,
      workflowRef: form.workflowRef.trim() || undefined,
      schema: outputSchema || undefined,
    },
    toolRefs: refItems(form.toolRefs),
    skillRefs: refItems(form.skillRefs),
    knowledgeRefs: refItems(form.knowledgeRefs),
    mcpRefs: refItems(form.mcpRefs),
    collaboration: { agentTools, handoffs },
    guardrails: {
      input: form.inputGuardrailRefs.map(ref => ({ ref: ref.trim(), execution: 'blocking' })).filter(item => item.ref),
      output: form.outputGuardrailRefs.map(ref => ({ ref: ref.trim() })).filter(item => item.ref),
    },
    runtimeDefaults: {
      maxTurns: form.maxTurns,
      timeoutMs: form.timeoutMs,
      maxAgentDepth: form.maxAgentDepth,
      toolConcurrency: form.toolConcurrency,
      stateStrategy: form.stateStrategy,
      tracing: {
        enabled: form.tracingEnabled,
        includeSensitiveData: false,
        workflowName: form.workflowName.trim() || form.code.trim(),
      },
    },
    extensions: {},
  }
  return {
    code: form.code.trim(),
    name: form.name.trim(),
    description: form.description.trim(),
    enabled: form.enabled,
    status: editor.saved.value?.status || 'DRAFT',
    draftVersion: editor.saved.value?.draftVersion || editor.currentVersion.value,
    labels: labels || {},
    apiVersion: 'ai.platform/v1alpha1',
    kind: 'Agent',
    metadata: {
      code: form.code.trim(),
      version: editor.currentVersion.value,
      name: form.name.trim(),
      description: form.description.trim(),
      labels: labels || {},
    },
    spec,
  }
}

const editor = useDefinitionEditor<AgentDefinition>({
  routeBase: '/settings/system/agents', label: 'Agent', get: getAgent, create: createAgent, update: updateAgent,
  validate: validateAgent, publish: publishAgent, getPayload, apply, reset,
})

function addCollaborator(mode: 'AS_TOOL' | 'HANDOFF') {
  form.collaborators.push({ targetAgentRef: '', mode, toolName: '', description: '' })
}

function removeCollaborator(index: number) {
  form.collaborators.splice(index, 1)
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) return
  await editor.save()
}

async function checkCompatibility() {
  if (!editor.currentCode.value) {
    ElMessage.warning('请先保存 Agent 草稿')
    return
  }
  const definition = await editor.save()
  if (!definition) return
  compatibilityLoading.value = true
  try {
    const report = await getAgentCompatibility(editor.currentCode.value, editor.currentVersion.value)
    compatibilitySummary.value = report.compatible === false
      ? report.message || report.issues?.[0]?.message || 'Python / TypeScript 兼容性检查未通过'
      : report.message || 'Python / TypeScript Adapter 兼容性检查通过'
  }
  catch (error) {
    compatibilitySummary.value = error instanceof Error ? error.message : '兼容性检查失败'
  }
  finally {
    compatibilityLoading.value = false
  }
}

async function runTest(payload: Record<string, unknown>) {
  const definition = await editor.save()
  if (!definition) throw new Error('Agent 草稿保存失败，未执行测试')
  return testAgent(editor.currentCode.value, editor.currentVersion.value, payload)
}

onMounted(editor.load)
</script>

<template>
  <ManagementEditorShell
    :title="editor.isCreate.value ? '新增 Agent' : `编辑 Agent · ${form.name || form.code}`"
    description="保存跨 Runtime 的中立 AgentManifest；发布前必须通过引用、图关系和双 Adapter 兼容性校验。"
    :status="editor.status.value"
    :version="editor.currentVersion.value"
    :loading="editor.loading.value"
    :saving="editor.saving.value"
    :validating="editor.validating.value"
    :publishing="editor.publishing.value"
    :report="editor.report.value"
    @back="editor.back"
    @save="save"
    @validate="editor.validate"
    @publish="editor.publish"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" status-icon>
      <el-tabs>
        <el-tab-pane label="基础与指令">
          <LayoutFormGrid :columns="2">
            <LayoutFormGridItem><el-form-item label="Agent 编码" prop="code"><el-input v-model="form.code" :disabled="!editor.isCreate.value" placeholder="home-assistant" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="Agent 名称" prop="name"><el-input v-model="form.name" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem span="full"><el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="模型引用" prop="modelRef"><el-input v-model="form.modelRef" placeholder="model://default-quality" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="启用状态"><el-switch v-model="form.enabled" inline-prompt active-text="启用" inactive-text="停用" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem span="full"><el-form-item label="Instructions" prop="instructions"><AppCodeEditor v-model="form.instructions" format="markdown" min-height="280px" :max-rows="18" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="Labels JSON"><AppCodeEditor v-model="form.labelsText" format="json" min-height="160px" :max-rows="10" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="Model Settings JSON"><AppCodeEditor v-model="form.modelSettingsText" format="json" min-height="160px" :max-rows="10" /></el-form-item></LayoutFormGridItem>
          </LayoutFormGrid>
        </el-tab-pane>

        <el-tab-pane label="能力引用">
          <LayoutFormGrid :columns="2">
            <LayoutFormGridItem><el-form-item label="Tool 引用"><el-select v-model="form.toolRefs" multiple filterable allow-create default-first-option placeholder="tool://kb-search/v2" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="Skill 引用"><el-select v-model="form.skillRefs" multiple filterable allow-create default-first-option placeholder="skill://policy/v1" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="Knowledge 引用"><el-select v-model="form.knowledgeRefs" multiple filterable allow-create default-first-option placeholder="knowledge://business-default" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="MCP 引用"><el-select v-model="form.mcpRefs" multiple filterable allow-create default-first-option placeholder="mcp://internal-tools/v1" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="输入 Guardrail"><el-select v-model="form.inputGuardrailRefs" multiple filterable allow-create default-first-option placeholder="guardrail://input-policy/v1" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="输出 Guardrail"><el-select v-model="form.outputGuardrailRefs" multiple filterable allow-create default-first-option placeholder="guardrail://output-safety/v1" /></el-form-item></LayoutFormGridItem>
          </LayoutFormGrid>
        </el-tab-pane>

        <el-tab-pane label="多 Agent 协作">
          <div class="agent-editor__section-actions">
            <el-button :icon="Plus" @click="addCollaborator('AS_TOOL')">添加 Agent as Tool</el-button>
            <el-button :icon="Plus" @click="addCollaborator('HANDOFF')">添加 Handoff</el-button>
          </div>
          <el-table :data="form.collaborators" border empty-text="尚未配置协作 Agent">
            <el-table-column label="模式" width="150"><template #default="{ row }"><el-select v-model="row.mode"><el-option label="Agent as Tool" value="AS_TOOL" /><el-option label="Handoff" value="HANDOFF" /></el-select></template></el-table-column>
            <el-table-column label="目标 Agent 引用" min-width="220"><template #default="{ row }"><el-input v-model="row.targetAgentRef" placeholder="agent://specialist/v1" /></template></el-table-column>
            <el-table-column label="Tool 名称" min-width="180"><template #default="{ row }"><el-input v-model="row.toolName" :disabled="row.mode === 'HANDOFF'" placeholder="analyze_requirement" /></template></el-table-column>
            <el-table-column label="说明" min-width="240"><template #default="{ row }"><el-input v-model="row.description" /></template></el-table-column>
            <el-table-column label="操作" width="76" align="center"><template #default="{ $index }"><el-button link type="danger" :icon="Delete" aria-label="删除协作项" @click="removeCollaborator($index)" /></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="输出与运行策略">
          <LayoutFormGrid :columns="2">
            <LayoutFormGridItem><el-form-item label="输出模式"><el-select v-model="form.outputMode"><el-option label="Text" value="text" /><el-option label="JSON Schema" value="jsonSchema" /><el-option label="Artifact Set" value="artifactSet" /></el-select></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="默认 Workflow"><el-input v-model="form.workflowRef" placeholder="workflow://home-chat-output/v3" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem span="full"><el-form-item label="输出 JSON Schema"><AppCodeEditor v-model="form.outputSchemaText" format="json" min-height="220px" :max-rows="14" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="最大 Turns"><el-input-number v-model="form.maxTurns" :min="1" :max="100" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="超时（毫秒）"><el-input-number v-model="form.timeoutMs" :min="1000" :max="900000" :step="1000" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="最大 Agent 深度"><el-input-number v-model="form.maxAgentDepth" :min="1" :max="8" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="Tool 并发数"><el-input-number v-model="form.toolConcurrency" :min="1" :max="16" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="State Strategy"><el-select v-model="form.stateStrategy"><el-option label="Application Replay" value="applicationReplay" /></el-select></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="Tracing"><el-switch v-model="form.tracingEnabled" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="Workflow Name"><el-input v-model="form.workflowName" /></el-form-item></LayoutFormGridItem>
          </LayoutFormGrid>
        </el-tab-pane>

        <el-tab-pane label="兼容性">
          <el-alert type="info" :closable="false" title="发布前应同时通过 PythonAdapter 与 TypeScriptAdapter 编译检查。" />
          <el-button class="agent-editor__compatibility-button" :loading="compatibilityLoading" @click="checkCompatibility">检查 Runtime 兼容性</el-button>
          <el-result v-if="compatibilitySummary" icon="info" title="兼容性结果" :sub-title="compatibilitySummary" />
        </el-tab-pane>

        <el-tab-pane label="测试运行">
          <DefinitionTestRunPanel
            :execute="runTest"
            :disabled="editor.isCreate.value || !editor.currentCode.value"
            input-hint="输入消息、上下文或运行参数，按当前 Agent 草稿版本执行隔离测试。"
          />
        </el-tab-pane>
      </el-tabs>
    </el-form>
  </ManagementEditorShell>
</template>

<style scoped>
.agent-editor__section-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--app-space-2);
  margin-bottom: var(--app-space-4);
}

.agent-editor__compatibility-button {
  margin-top: var(--app-space-4);
}

:deep(.el-select),
:deep(.el-input-number) {
  width: 100%;
}
</style>
