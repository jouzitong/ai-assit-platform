<script setup lang="ts">
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { AppCodeEditor, LayoutFormGrid, LayoutFormGridItem } from '../../../../components'
import { createTool, getTool, publishTool, testTool, updateTool, validateTool } from '../api/tools'
import DefinitionTestRunPanel from '../components/DefinitionTestRunPanel.vue'
import ManagementEditorShell from '../components/ManagementEditorShell.vue'
import { useDefinitionEditor } from '../composables/useDefinitionEditor'
import type { ToolBinding, ToolDefinition } from '../types'

type BindingDraft = ToolBinding & { secretRefsText: string; configText: string }

const formRef = ref<FormInstance>()
const form = reactive({
  code: '', name: '', description: '', enabled: true, timeoutMs: 30000,
  inputSchemaText: '{\n  "type": "object",\n  "properties": {},\n  "additionalProperties": false\n}',
  outputSchemaText: '{\n  "type": "object"\n}',
  permissionPolicyText: '{}', approvalPolicyText: '{}', bindings: [] as BindingDraft[],
})

const rules: FormRules = {
  code: [
    { required: true, message: '请输入 Tool 编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9-]{1,63}$/, message: '使用小写字母、数字和短横线', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入 Tool 名称', trigger: 'blur' }],
}

function reset() {
  Object.assign(form, {
    code: '', name: '', description: '', enabled: true, timeoutMs: 30000,
    inputSchemaText: '{\n  "type": "object",\n  "properties": {},\n  "additionalProperties": false\n}',
    outputSchemaText: '{\n  "type": "object"\n}', permissionPolicyText: '{}', approvalPolicyText: '{}', bindings: [],
  })
  addBinding()
}

function apply(value: ToolDefinition) {
  form.code = value.code || ''
  form.name = value.name || ''
  form.description = value.description || ''
  form.enabled = value.enabled !== false
  form.timeoutMs = Number(value.timeoutMs || 30000)
  form.inputSchemaText = JSON.stringify(value.inputSchema || { type: 'object', properties: {}, additionalProperties: false }, null, 2)
  form.outputSchemaText = JSON.stringify(value.outputSchema || { type: 'object' }, null, 2)
  form.permissionPolicyText = JSON.stringify(value.permissionPolicy || {}, null, 2)
  form.approvalPolicyText = JSON.stringify(value.approvalPolicy || {}, null, 2)
  form.bindings = (value.bindings || []).map(binding => ({
    ...binding,
    secretRefsText: (binding.secretRefs || []).join(', '),
    configText: JSON.stringify(binding.config || {}, null, 2),
  }))
}

function addBinding() {
  form.bindings.push({
    bindingType: 'HTTP', runtimeType: '', endpointRef: '', packageUri: '', entrypoint: '',
    secretRefs: [], secretRefsText: '', enabled: true, config: {}, configText: '{}',
  })
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
  const inputSchema = parseObject(form.inputSchemaText, 'Input Schema')
  const outputSchema = parseObject(form.outputSchemaText, 'Output Schema')
  const permissionPolicy = parseObject(form.permissionPolicyText, 'Permission Policy')
  const approvalPolicy = parseObject(form.approvalPolicyText, 'Approval Policy')
  if (!inputSchema || !outputSchema || !permissionPolicy || !approvalPolicy) return null
  if (!form.bindings.length) {
    ElMessage.error('至少配置一个 Runtime Binding')
    return null
  }
  const bindings: ToolBinding[] = []
  for (const item of form.bindings) {
    if (!item.bindingType) {
      ElMessage.error('Runtime Binding 必须选择类型')
      return null
    }
    const config = parseObject(item.configText, `${item.bindingType} Binding 配置`)
    if (!config) return null
    if (['HTTP', 'JAVA_INTERNAL'].includes(item.bindingType) && !item.endpointRef?.trim()) {
      ElMessage.error(`${item.bindingType} Binding 必须填写 Endpoint 引用`)
      return null
    }
    if (['PYTHON_MODULE', 'JAVASCRIPT_MODULE'].includes(item.bindingType)
      && (!item.packageUri?.trim() || !item.entrypoint?.trim())) {
      ElMessage.error(`${item.bindingType} Binding 必须填写 Package URI 和 Entrypoint`)
      return null
    }
    const secretRefs = item.secretRefsText.split(',').map(value => value.trim()).filter(Boolean)
    if (secretRefs.some(ref => !ref.startsWith('secret://'))) {
      ElMessage.error('Secret 只能保存 secret:// 引用')
      return null
    }
    bindings.push({
      bindingType: item.bindingType, runtimeType: item.runtimeType?.trim(), endpointRef: item.endpointRef?.trim(),
      packageUri: item.packageUri?.trim(), entrypoint: item.entrypoint?.trim(),
      secretRefs,
      enabled: item.enabled !== false, config,
    })
  }
  return {
    code: form.code.trim(), name: form.name.trim(), description: form.description.trim(), enabled: form.enabled,
    status: editor.saved.value?.status || 'DRAFT', draftVersion: editor.saved.value?.draftVersion || editor.currentVersion.value,
    timeoutMs: form.timeoutMs, inputSchema, outputSchema, permissionPolicy, approvalPolicy, bindings,
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
    description="Tool 是具备 JSON Schema、权限与审批策略的确定性能力；Secret 只能保存引用。"
    :status="editor.status.value" :version="editor.currentVersion.value" :loading="editor.loading.value"
    :saving="editor.saving.value" :validating="editor.validating.value" :publishing="editor.publishing.value"
    :report="editor.report.value" @back="editor.back" @save="save" @validate="editor.validate" @publish="editor.publish"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" status-icon>
      <el-tabs>
        <el-tab-pane label="基础与 Schema">
          <LayoutFormGrid :columns="2">
            <LayoutFormGridItem><el-form-item label="Tool 编码" prop="code"><el-input v-model="form.code" :disabled="!editor.isCreate.value" placeholder="kb-search" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem span="full"><el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="超时（毫秒）"><el-input-number v-model="form.timeoutMs" :min="100" :max="900000" :step="1000" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="启用状态"><el-switch v-model="form.enabled" inline-prompt active-text="启用" inactive-text="停用" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="Input JSON Schema"><AppCodeEditor v-model="form.inputSchemaText" format="json" min-height="300px" :max-rows="18" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="Output JSON Schema"><AppCodeEditor v-model="form.outputSchemaText" format="json" min-height="300px" :max-rows="18" /></el-form-item></LayoutFormGridItem>
          </LayoutFormGrid>
        </el-tab-pane>

        <el-tab-pane label="权限与审批">
          <LayoutFormGrid :columns="2">
            <LayoutFormGridItem><el-form-item label="Permission Policy"><AppCodeEditor v-model="form.permissionPolicyText" format="json" min-height="320px" :max-rows="20" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="Approval Policy"><AppCodeEditor v-model="form.approvalPolicyText" format="json" min-height="320px" :max-rows="20" /></el-form-item></LayoutFormGridItem>
          </LayoutFormGrid>
        </el-tab-pane>

        <el-tab-pane label="Runtime Bindings">
          <div class="tool-editor__toolbar"><el-button :icon="Plus" @click="addBinding">添加 Binding</el-button></div>
          <el-collapse>
            <el-collapse-item v-for="(binding, index) in form.bindings" :key="index" :name="index">
              <template #title><strong>{{ binding.bindingType || `Binding ${index + 1}` }}</strong><el-tag effect="plain">{{ binding.runtimeType || '未配置 Runtime' }}</el-tag></template>
              <div class="tool-editor__item-actions"><el-button link type="danger" :icon="Delete" @click.stop="form.bindings.splice(index, 1)">删除</el-button></div>
              <LayoutFormGrid :columns="2">
                <LayoutFormGridItem><el-form-item label="Binding 类型"><el-select v-model="binding.bindingType"><el-option label="HTTP" value="HTTP" /><el-option label="MCP" value="MCP" /><el-option label="Java Internal" value="JAVA_INTERNAL" /><el-option label="Hosted" value="HOSTED" /><el-option label="Python Module" value="PYTHON_MODULE" /><el-option label="JavaScript Module" value="JAVASCRIPT_MODULE" /></el-select></el-form-item></LayoutFormGridItem>
                <LayoutFormGridItem><el-form-item label="Runtime 类型"><el-input v-model="binding.runtimeType" placeholder="OPENAI_AGENTS_PYTHON" /></el-form-item></LayoutFormGridItem>
                <LayoutFormGridItem><el-form-item label="Endpoint 引用"><el-input v-model="binding.endpointRef" placeholder="endpoint://internal-api" /></el-form-item></LayoutFormGridItem>
                <LayoutFormGridItem><el-form-item label="Package URI"><el-input v-model="binding.packageUri" placeholder="package://tool-runtime/v1" /></el-form-item></LayoutFormGridItem>
                <LayoutFormGridItem><el-form-item label="Entrypoint"><el-input v-model="binding.entrypoint" placeholder="tools/search.py:run" /></el-form-item></LayoutFormGridItem>
                <LayoutFormGridItem><el-form-item label="Secret 引用（逗号分隔）"><el-input v-model="binding.secretRefsText" placeholder="secret://kb-token" /></el-form-item></LayoutFormGridItem>
                <LayoutFormGridItem><el-form-item label="启用"><el-switch v-model="binding.enabled" /></el-form-item></LayoutFormGridItem>
                <LayoutFormGridItem span="full"><el-form-item label="Binding Config"><AppCodeEditor v-model="binding.configText" format="json" min-height="180px" :max-rows="12" /></el-form-item></LayoutFormGridItem>
              </LayoutFormGrid>
            </el-collapse-item>
          </el-collapse>
        </el-tab-pane>

        <el-tab-pane label="测试运行">
          <DefinitionTestRunPanel
            :execute="runTest"
            :disabled="editor.isCreate.value || !editor.currentCode.value"
            input-hint="输入符合 Input JSON Schema 的参数，验证当前 Tool 版本的 Binding、权限和返回契约。"
          />
        </el-tab-pane>
      </el-tabs>
    </el-form>
  </ManagementEditorShell>
</template>

<style scoped>
.tool-editor__toolbar,
.tool-editor__item-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--app-space-3);
}

:deep(.el-collapse-item__title) {
  gap: var(--app-space-3);
}

:deep(.el-select),
:deep(.el-input-number) {
  width: 100%;
}
</style>
