<script setup lang="ts">
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { AppCodeEditor, LayoutFormGrid, LayoutFormGridItem } from '../../../../components'
import { createTool, getTool, publishTool, testTool, updateTool, validateTool } from '../api/tools'
import DefinitionTestRunPanel from '../components/DefinitionTestRunPanel.vue'
import ManagementEditorShell from '../components/ManagementEditorShell.vue'
import { useDefinitionEditor } from '../composables/useDefinitionEditor'
import type { ToolDefinition, ToolImplementationRuntime } from '../types'

const AGENT_RUNTIME_OPTIONS = [
  { label: 'OpenAI Agents SDK · Python', value: 'OPENAI_AGENTS_PYTHON' },
  { label: 'OpenAI Agents SDK · TypeScript', value: 'OPENAI_AGENTS_TYPESCRIPT' },
]

const PYTHON_TEMPLATE = `import os


async def run(arguments, context):
    """Platform entrypoint. Return a JSON-serializable value."""
    token = (os.getenv("AI_AGENT_KB_SEARCH_TOKEN") or "").strip()
    query = str(arguments.get("query") or "").strip()
    return {
        "query": query,
        "tokenAvailable": bool(token),
        "config": context.get("config") or {},
    }
`

const JAVASCRIPT_TEMPLATE = `export async function run(args, context) {
  // Return a JSON-serializable value.
  const token = (process.env.AI_AGENT_KB_SEARCH_TOKEN ?? "").trim();
  const query = String(args.query ?? "").trim();
  return {
    query,
    tokenAvailable: Boolean(token),
    config: context.config ?? {},
  };
}
`

const formRef = ref<FormInstance>()
const form = reactive({
  code: '',
  name: '',
  description: '',
  enabled: true,
  timeoutMs: 30000,
  implementationRuntime: 'PYTHON' as ToolImplementationRuntime,
  compatibleAgentRuntimes: ['OPENAI_AGENTS_PYTHON', 'OPENAI_AGENTS_TYPESCRIPT'],
  sourceCode: PYTHON_TEMPLATE,
  inputSchemaText: '{\n  "type": "object",\n  "properties": {\n    "query": { "type": "string" }\n  },\n  "required": ["query"],\n  "additionalProperties": false\n}',
  outputSchemaText: '{\n  "type": "object"\n}',
  runtimeConfigText: '{}',
  permissionPolicyText: '{}',
  approvalPolicyText: '{}',
})

const codeFormat = computed(() => form.implementationRuntime === 'PYTHON' ? 'python' : 'javascript')

const rules: FormRules = {
  code: [
    { required: true, message: '请输入 Tool 编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_-]{1,63}$/, message: '使用小写字母、数字、下划线或短横线，长度 2-64', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入 Tool 名称', trigger: 'blur' }],
}

function reset() {
  Object.assign(form, {
    code: '',
    name: '',
    description: '',
    enabled: true,
    timeoutMs: 30000,
    implementationRuntime: 'PYTHON',
    compatibleAgentRuntimes: ['OPENAI_AGENTS_PYTHON', 'OPENAI_AGENTS_TYPESCRIPT'],
    sourceCode: PYTHON_TEMPLATE,
    inputSchemaText: '{\n  "type": "object",\n  "properties": {\n    "query": { "type": "string" }\n  },\n  "required": ["query"],\n  "additionalProperties": false\n}',
    outputSchemaText: '{\n  "type": "object"\n}',
    runtimeConfigText: '{}',
    permissionPolicyText: '{}',
    approvalPolicyText: '{}',
  })
}

function apply(value: ToolDefinition) {
  form.code = value.code || ''
  form.name = value.name || ''
  form.description = value.description || ''
  form.enabled = value.enabled !== false
  form.timeoutMs = Number(value.timeoutMs || 30000)
  form.implementationRuntime = value.implementationRuntime || 'PYTHON'
  form.compatibleAgentRuntimes = value.compatibleAgentRuntimes?.length
    ? [...value.compatibleAgentRuntimes]
    : ['OPENAI_AGENTS_PYTHON', 'OPENAI_AGENTS_TYPESCRIPT']
  form.sourceCode = value.sourceCode || (form.implementationRuntime === 'PYTHON' ? PYTHON_TEMPLATE : JAVASCRIPT_TEMPLATE)
  form.inputSchemaText = JSON.stringify(value.inputSchema || { type: 'object', properties: {}, additionalProperties: false }, null, 2)
  form.outputSchemaText = JSON.stringify(value.outputSchema || { type: 'object' }, null, 2)
  form.runtimeConfigText = JSON.stringify(value.runtimeConfig || {}, null, 2)
  form.permissionPolicyText = JSON.stringify(value.permissionPolicy || {}, null, 2)
  form.approvalPolicyText = JSON.stringify(value.approvalPolicy || {}, null, 2)
}

function parseObject(text: string, label: string) {
  try {
    const value = JSON.parse(text || '{}')
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error()
    return value as Record<string, unknown>
  }
  catch {
    ElMessage.error(`${label}必须是合法 JSON Object`)
    return null
  }
}

function getPayload(): ToolDefinition | null {
  if (!form.code.trim() || !form.name.trim()) {
    ElMessage.error('请填写 Tool 编码和名称')
    return null
  }
  if (!form.sourceCode.trim()) {
    ElMessage.error('请编写 Tool 代码')
    return null
  }
  if (!form.compatibleAgentRuntimes.length) {
    ElMessage.error('至少选择一个可调用的 Agent Runtime')
    return null
  }
  const inputSchema = parseObject(form.inputSchemaText, 'Input Schema')
  const outputSchema = parseObject(form.outputSchemaText, 'Output Schema')
  const runtimeConfig = parseObject(form.runtimeConfigText, 'Runtime Config')
  const permissionPolicy = parseObject(form.permissionPolicyText, 'Permission Policy')
  const approvalPolicy = parseObject(form.approvalPolicyText, 'Approval Policy')
  if (!inputSchema || !outputSchema || !runtimeConfig || !permissionPolicy || !approvalPolicy) return null
  return {
    code: form.code.trim(),
    name: form.name.trim(),
    description: form.description.trim(),
    enabled: form.enabled,
    status: editor.saved.value?.status || 'DRAFT',
    draftVersion: editor.saved.value?.draftVersion || editor.currentVersion.value,
    executionMode: 'MANAGED_CODE',
    implementationRuntime: form.implementationRuntime,
    compatibleAgentRuntimes: [...form.compatibleAgentRuntimes],
    sourceCode: form.sourceCode,
    timeoutMs: form.timeoutMs,
    inputSchema,
    outputSchema,
    runtimeConfig,
    permissionPolicy,
    approvalPolicy,
    bindings: [],
  }
}

const editor = useDefinitionEditor<ToolDefinition>({
  routeBase: '/settings/system/tools', label: 'Tool', get: getTool, create: createTool, update: updateTool,
  validate: validateTool, publish: publishTool, getPayload, apply, reset,
})

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) return
  await editor.save()
}

async function loadRuntimeTemplate() {
  if (form.sourceCode.trim()) {
    try {
      await ElMessageBox.confirm('载入模板会覆盖当前代码，是否继续？', '载入代码模板', {
        type: 'warning', confirmButtonText: '载入模板', cancelButtonText: '取消',
      })
    }
    catch {
      return
    }
  }
  form.sourceCode = form.implementationRuntime === 'PYTHON' ? PYTHON_TEMPLATE : JAVASCRIPT_TEMPLATE
}

async function runTest(payload: Record<string, unknown>) {
  const definition = await editor.save()
  if (!definition) throw new Error('Tool 草稿保存失败，未执行测试')
  return testTool(editor.currentCode.value, editor.currentVersion.value, payload)
}

onMounted(editor.load)
</script>

<template>
  <ManagementEditorShell
    :title="editor.isCreate.value ? '新增 Tool' : `编辑 Tool · ${form.name || form.code}`"
    description="在页面编写并测试 Python/JavaScript；发布后由 Java Tool Gateway 按不可变版本执行。"
    :status="editor.status.value" :version="editor.currentVersion.value" :loading="editor.loading.value"
    :saving="editor.saving.value" :validating="editor.validating.value" :publishing="editor.publishing.value"
    :report="editor.report.value" @back="editor.back" @save="save" @validate="editor.validate" @publish="editor.publish"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" status-icon>
      <el-tabs>
        <el-tab-pane label="代码实现">
          <LayoutFormGrid :columns="2">
            <LayoutFormGridItem><el-form-item label="Tool 编码" prop="code"><el-input v-model="form.code" :disabled="!editor.isCreate.value" placeholder="kb-search" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem span="full"><el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem>
              <el-form-item label="实现语言">
                <el-select v-model="form.implementationRuntime">
                  <el-option label="Python" value="PYTHON" />
                  <el-option label="JavaScript · Node.js" value="JAVASCRIPT" />
                </el-select>
              </el-form-item>
            </LayoutFormGridItem>
            <LayoutFormGridItem>
              <el-form-item label="可调用的 Agent Runtime">
                <el-select v-model="form.compatibleAgentRuntimes" multiple collapse-tags :max-collapse-tags="2">
                  <el-option v-for="option in AGENT_RUNTIME_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
                </el-select>
              </el-form-item>
            </LayoutFormGridItem>
            <LayoutFormGridItem span="full">
              <el-alert type="info" :closable="false" show-icon>
                Java 在每次执行前签发有效期 2 小时的临时 Token，并注入 AI_AGENT_KB_SEARCH_TOKEN 等环境变量；代码和配置中不保存 Secret。
              </el-alert>
            </LayoutFormGridItem>
            <LayoutFormGridItem span="full">
              <div class="tool-editor__code-heading">
                <div><strong>业务代码</strong><span>{{ form.implementationRuntime === 'PYTHON' ? '入口：run(arguments, context)' : '入口：export async function run(args, context)' }}</span></div>
                <el-button text type="primary" @click="loadRuntimeTemplate">载入模板</el-button>
              </div>
              <AppCodeEditor v-model="form.sourceCode" :format="codeFormat" :show-format-switcher="false" min-height="460px" :max-rows="28" />
            </LayoutFormGridItem>
          </LayoutFormGrid>
        </el-tab-pane>

        <el-tab-pane label="参数与配置">
          <LayoutFormGrid :columns="2">
            <LayoutFormGridItem><el-form-item label="超时（毫秒）"><el-input-number v-model="form.timeoutMs" :min="100" :max="300000" :step="1000" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="启用状态"><el-switch v-model="form.enabled" inline-prompt active-text="启用" inactive-text="停用" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="Input JSON Schema"><AppCodeEditor v-model="form.inputSchemaText" format="json" min-height="300px" :max-rows="18" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="Output JSON Schema"><AppCodeEditor v-model="form.outputSchemaText" format="json" min-height="300px" :max-rows="18" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem span="full"><el-form-item label="Runtime Config（通过 context.config 读取）"><AppCodeEditor v-model="form.runtimeConfigText" format="json" min-height="220px" :max-rows="14" /></el-form-item></LayoutFormGridItem>
          </LayoutFormGrid>
        </el-tab-pane>

        <el-tab-pane label="权限与审批">
          <LayoutFormGrid :columns="2">
            <LayoutFormGridItem><el-form-item label="Permission Policy"><AppCodeEditor v-model="form.permissionPolicyText" format="json" min-height="320px" :max-rows="20" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="Approval Policy"><AppCodeEditor v-model="form.approvalPolicyText" format="json" min-height="320px" :max-rows="20" /></el-form-item></LayoutFormGridItem>
          </LayoutFormGrid>
        </el-tab-pane>

        <el-tab-pane label="测试运行">
          <DefinitionTestRunPanel
            :execute="runTest"
            input-hint="执行测试会先保存当前草稿，再由 Java 启动对应 Python/Node.js 子进程运行代码。"
          />
        </el-tab-pane>
      </el-tabs>
    </el-form>
  </ManagementEditorShell>
</template>

<style scoped>
.tool-editor__code-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-4);
  margin-bottom: var(--app-space-3);
}

.tool-editor__code-heading > div {
  display: flex;
  align-items: baseline;
  gap: var(--app-space-3);
}

.tool-editor__code-heading span {
  color: var(--system-text-muted);
  font-size: var(--app-font-size-sm);
}

:deep(.el-select),
:deep(.el-input-number) {
  width: 100%;
}
</style>
