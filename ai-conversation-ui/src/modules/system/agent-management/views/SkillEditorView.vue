<script setup lang="ts">
import { Document, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules, type UploadFile, type UploadFiles } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AppCodeEditor, LayoutFormGrid, LayoutFormGridItem } from '../../../../components'
import {
  createFormSkill,
  getSkill,
  importSkillPackage,
  inspectSkillPackage,
  publishSkill,
  updateSkill,
  validateSkill,
} from '../api/skills'
import { listTools } from '../api/tools'
import ManagementEditorShell from '../components/ManagementEditorShell.vue'
import { useDefinitionEditor } from '../composables/useDefinitionEditor'
import type { SkillDefinition, SkillPackageInspection, SkillSourceType, ToolDefinition } from '../types'

const router = useRouter()
const formRef = ref<FormInstance>()
const uploadFiles = ref<UploadFile[]>([])
const inspection = ref<SkillPackageInspection | null>(null)
const packageFiles = ref<SkillDefinition['files']>([])
const packageManifest = ref<Record<string, unknown>>({})
const inspecting = ref(false)
const importing = ref(false)
const toolOptions = ref<ToolDefinition[]>([])
const toolOptionsLoading = ref(false)
const toolOptionsError = ref('')

const form = reactive({
  sourceType: 'FORM' as SkillSourceType,
  code: '', name: '', description: '', license: '', compatibility: '', content: '',
  compatibleRuntimes: [] as string[], toolRefs: [] as string[], enabled: true,
})

const rules: FormRules = {
  code: [
    { required: true, message: '请输入 Skill 编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9-]{1,63}$/, message: '使用小写字母、数字和短横线', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入 Skill 名称', trigger: 'blur' }],
}

const inspectionRisks = computed(() => {
  const report = inspection.value
  if (!report) return []
  const normalized = (values: Array<string | { message: string; severity?: string; path?: string }> = [], fallbackSeverity: string) =>
    values.map(value => typeof value === 'string'
      ? { message: value, severity: fallbackSeverity }
      : value)
  return [
    ...(report.risks || []),
    ...normalized(report.errors, 'ERROR'),
    ...normalized(report.warnings, 'WARNING'),
  ]
})
const inspectionFiles = computed(() => (inspection.value?.files || packageFiles.value || []).map((file) => ({
  ...file,
  name: file.name || file.path?.split('/').filter(Boolean).slice(-1)[0] || file.path || 'file',
})))
const displayManifest = computed(() => inspection.value?.manifest || packageManifest.value)
const hasPackageReport = computed(() => Boolean(inspection.value || inspectionFiles.value.length || Object.keys(displayManifest.value).length))
const riskCount = computed(() => inspectionRisks.value.length)
const inspectionValid = computed(() => Boolean(
  inspection.value?.draftId
  && inspection.value.valid !== false
  && inspection.value.compatibility?.valid !== false
  && inspection.value.compatibility?.compatible !== false
))
const toolOptionsEmptyText = computed(() => toolOptionsError.value || '暂无已发布且启用的 Tool')

function toolRef(tool: ToolDefinition) {
  return `tool://${tool.code}/v${tool.currentPublishedVersion}`
}

function toolOptionLabel(tool: ToolDefinition) {
  return `${tool.name || tool.code} · ${tool.code} · v${tool.currentPublishedVersion}`
}

async function loadToolOptions() {
  toolOptionsLoading.value = true
  toolOptionsError.value = ''
  try {
    const result = await listTools({ enabled: true })
    const rows = Array.isArray(result) ? result : (result.list || [])
    toolOptions.value = rows
      .filter(tool => tool.enabled !== false && Number.isInteger(tool.currentPublishedVersion))
      .sort((left, right) => (left.name || left.code).localeCompare(right.name || right.code, 'zh-CN'))
  }
  catch (error) {
    toolOptions.value = []
    toolOptionsError.value = error instanceof Error ? error.message : 'Tool 列表加载失败'
  }
  finally {
    toolOptionsLoading.value = false
  }
}

function reset() {
  Object.assign(form, {
    sourceType: 'FORM', code: '', name: '', description: '', license: '', compatibility: '', content: '',
    compatibleRuntimes: [], toolRefs: [], enabled: true,
  })
  uploadFiles.value = []
  inspection.value = null
  packageFiles.value = []
  packageManifest.value = {}
}

function apply(value: SkillDefinition) {
  form.sourceType = value.sourceType || 'FORM'
  form.code = value.code || ''
  form.name = value.name || ''
  form.description = value.description || ''
  form.license = value.license || ''
  form.compatibility = value.compatibility || ''
  form.compatibleRuntimes = [...(value.compatibleRuntimes || [])]
  form.content = value.content || ''
  form.toolRefs = [...(value.toolRefs || [])]
  form.enabled = value.enabled !== false
  packageFiles.value = [...(value.files || [])]
  packageManifest.value = { ...(value.manifest || {}) }
}

function getPayload(): SkillDefinition | null {
  if (!form.code.trim() || !form.name.trim()) {
    ElMessage.error('请填写 Skill 编码和名称')
    return null
  }
  if (form.sourceType === 'FORM' && !form.content.trim()) {
    ElMessage.error('表单模式必须填写 SKILL.md 正文')
    return null
  }
  if (form.sourceType === 'ZIP' && !inspection.value?.draftId && editor.isCreate.value) {
    ElMessage.error('请先上传并 Inspect ZIP 包')
    return null
  }
  const inspectedCode = inspection.value?.skill?.code
  if (form.sourceType === 'ZIP' && inspectedCode && form.code.trim() !== inspectedCode) {
    ElMessage.error(`Skill 编码必须与 SKILL.md name 保持一致：${inspectedCode}`)
    return null
  }
  return {
    code: form.code.trim(), name: form.name.trim(), description: form.description.trim(), sourceType: form.sourceType,
    license: form.license.trim(), compatibility: form.compatibility.trim(),
    content: form.sourceType === 'FORM' ? form.content : undefined,
    compatibleRuntimes: form.compatibleRuntimes.filter(Boolean), toolRefs: form.toolRefs.filter(Boolean), enabled: form.enabled,
    status: editor.saved.value?.status || 'DRAFT', draftVersion: editor.saved.value?.draftVersion || editor.currentVersion.value,
    entrypoint: 'SKILL.md',
  }
}

async function create(payload: SkillDefinition) {
  if (payload.sourceType === 'ZIP') {
    const draftId = inspection.value?.draftId
    if (!draftId) throw new Error('ZIP 包尚未完成 Inspect')
    importing.value = true
    try {
      return await importSkillPackage(draftId, payload)
    }
    finally {
      importing.value = false
    }
  }
  return createFormSkill(payload)
}

const editor = useDefinitionEditor<SkillDefinition>({
  routeBase: '/settings/system/skills', label: 'Skill', get: getSkill, create, update: updateSkill,
  validate: validateSkill, publish: publishSkill, getPayload, apply, reset,
})

function handleUploadChange(_file: UploadFile, files: UploadFiles) {
  uploadFiles.value = files.slice(-1)
  inspection.value = null
}

function handleUploadRemove() {
  uploadFiles.value = []
  inspection.value = null
}

async function inspectPackage() {
  const raw = uploadFiles.value[0]?.raw
  if (!raw) {
    ElMessage.warning('请选择 ZIP 包')
    return
  }
  if (!raw.name.toLowerCase().endsWith('.zip')) {
    ElMessage.error('首期只接受 ZIP 包')
    return
  }
  inspecting.value = true
  try {
    inspection.value = await inspectSkillPackage(raw)
    const parsed = inspection.value.skill
    const manifest = inspection.value.manifest || {}
    const manifestName = typeof manifest.name === 'string' ? manifest.name : ''
    const manifestDescription = typeof manifest.description === 'string' ? manifest.description : ''
    const manifestLicense = typeof manifest.license === 'string' ? manifest.license : ''
    const manifestCompatibility = typeof manifest.compatibility === 'string' ? manifest.compatibility : ''
    const allowedTools = Array.isArray(manifest['allowed-tools'])
      ? manifest['allowed-tools'].filter((value): value is string => typeof value === 'string')
      : []
    const compatibleRuntimes = Array.isArray(manifest.compatibleRuntimes)
      ? manifest.compatibleRuntimes.filter((value): value is string => typeof value === 'string')
      : []
    form.code = parsed?.code || manifestName || form.code
    form.name = parsed?.name || manifestName || form.name
    form.description = parsed?.description || manifestDescription || form.description
    form.license = parsed?.license || manifestLicense || form.license
    form.compatibility = parsed?.compatibility || manifestCompatibility || form.compatibility
    form.compatibleRuntimes = parsed?.compatibleRuntimes
      || (compatibleRuntimes.length ? compatibleRuntimes : form.compatibleRuntimes)
    form.content = parsed?.content || form.content
    form.toolRefs = parsed?.toolRefs || (allowedTools.length ? allowedTools : form.toolRefs)
    packageFiles.value = [...(inspection.value.files || [])]
    packageManifest.value = { ...(inspection.value.manifest || {}) }
    ElMessage.success(inspectionValid.value ? 'ZIP Inspect 通过，可确认导入' : 'Inspect 完成，请处理校验问题')
  }
  catch (error) {
    inspection.value = null
    ElMessage.error(error instanceof Error ? error.message : 'ZIP Inspect 失败')
  }
  finally {
    inspecting.value = false
  }
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) return
  await editor.save()
}

async function importInspectedPackage() {
  if (!inspectionValid.value) {
    ElMessage.warning('Inspect 尚未通过')
    return
  }
  const value = await editor.save()
  if (value) await router.replace(`/settings/system/skills/${encodeURIComponent(value.code)}`)
}

onMounted(async () => {
  await Promise.all([editor.load(), loadToolOptions()])
})
</script>

<template>
  <ManagementEditorShell
    :title="editor.isCreate.value ? '新增 Skill' : `编辑 Skill · ${form.name || form.code}`"
    description="FORM 和 ZIP 上传最终生成相同的不可变 Skill 包；ZIP 必须先经过隔离 Inspect，再确认导入。"
    :status="editor.status.value" :version="editor.currentVersion.value" :loading="editor.loading.value"
    :saving="editor.saving.value || importing" :validating="editor.validating.value" :publishing="editor.publishing.value"
    :save-disabled="form.sourceType === 'ZIP' && editor.isCreate.value"
    :validate-disabled="form.sourceType === 'ZIP' && editor.isCreate.value"
    :publish-disabled="form.sourceType === 'ZIP' && editor.isCreate.value"
    :report="editor.report.value" @back="editor.back" @save="save" @validate="editor.validate" @publish="editor.publish"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" status-icon>
      <el-tabs>
        <el-tab-pane label="定义与内容">
          <el-form-item label="创建方式">
            <el-radio-group v-model="form.sourceType" :disabled="!editor.isCreate.value">
              <el-radio-button value="FORM">表单创建</el-radio-button>
              <el-radio-button value="ZIP">ZIP 包上传</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <LayoutFormGrid :columns="2">
            <LayoutFormGridItem><el-form-item label="Skill 编码" prop="code"><el-input v-model="form.code" :disabled="!editor.isCreate.value || (form.sourceType === 'ZIP' && inspectionValid)" placeholder="analysis-policy" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem span="full"><el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item></LayoutFormGridItem>
            <LayoutFormGridItem span="full">
              <el-form-item label="兼容 Runtime">
                <el-select v-model="form.compatibleRuntimes" :disabled="form.sourceType === 'ZIP' && !editor.isCreate.value" multiple filterable allow-create default-first-option placeholder="OPENAI_AGENTS_PYTHON">
                  <el-option label="OpenAI Agents Python" value="OPENAI_AGENTS_PYTHON" />
                  <el-option label="OpenAI Agents TypeScript" value="OPENAI_AGENTS_TYPESCRIPT" />
                </el-select>
              </el-form-item>
            </LayoutFormGridItem>
            <LayoutFormGridItem span="full">
              <el-form-item label="Tool 映射">
                <el-select
                  v-model="form.toolRefs"
                  multiple
                  filterable
                  :loading="toolOptionsLoading"
                  :no-data-text="toolOptionsEmptyText"
                  placeholder="选择已发布 Tool"
                >
                  <el-option
                    v-for="tool in toolOptions"
                    :key="toolRef(tool)"
                    :label="toolOptionLabel(tool)"
                    :value="toolRef(tool)"
                  />
                </el-select>
                <el-text type="info" size="small">仅展示已发布且启用的 Tool，引用固定到具体发布版本。</el-text>
              </el-form-item>
            </LayoutFormGridItem>
            <LayoutFormGridItem><el-form-item label="启用状态"><el-switch v-model="form.enabled" inline-prompt active-text="启用" inactive-text="停用" /></el-form-item></LayoutFormGridItem>
          </LayoutFormGrid>

          <el-form-item v-if="form.sourceType === 'FORM'" label="SKILL.md 正文（不含 YAML Frontmatter）" required>
            <AppCodeEditor v-model="form.content" format="markdown" min-height="420px" :max-rows="26" placeholder="编写 Skill 工作方法和使用规则；Frontmatter 由服务端根据上方字段生成" />
          </el-form-item>

          <div v-else class="skill-package">
            <el-alert
              v-if="!editor.isCreate.value"
              type="info"
              :closable="false"
              title="ZIP 包内容保持不可变；这里可更新目录信息、Tool 映射和启用状态。需要替换文件时请创建新版本。"
            />
            <template v-else>
              <el-upload
                v-model:file-list="uploadFiles"
                drag
                :auto-upload="false"
                :limit="1"
                accept=".zip,application/zip"
                :disabled="!editor.isCreate.value || inspecting || importing"
                @change="handleUploadChange"
                @remove="handleUploadRemove"
              >
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">拖入 Skill ZIP，或<em>点击选择</em></div>
                <template #tip><div class="el-upload__tip">最大 50 MB；顶层目录必须包含唯一 SKILL.md。</div></template>
              </el-upload>
              <div class="skill-package__actions">
                <el-button :loading="inspecting" :disabled="!uploadFiles.length" @click="inspectPackage">Inspect ZIP</el-button>
                <el-button type="primary" :loading="importing" :disabled="!inspectionValid" @click="importInspectedPackage">确认导入草稿</el-button>
              </div>
            </template>
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="form.sourceType === 'ZIP'" label="Inspect 报告">
          <el-empty v-if="!hasPackageReport" description="上传 ZIP 并执行 Inspect 后展示结果" />
          <template v-else>
            <el-alert
              v-if="inspection"
              :type="inspectionValid ? (riskCount ? 'warning' : 'success') : 'error'"
              :title="inspectionValid ? `结构校验完成，发现 ${riskCount} 个风险项` : '包校验未通过'"
              :closable="false"
              show-icon
            />
            <LayoutFormGrid :columns="2" class="skill-package__report">
              <LayoutFormGridItem>
                <h3>文件树</h3>
                <el-tree :data="inspectionFiles" node-key="path" :props="{ label: 'name', children: 'children' }" default-expand-all>
                  <template #default="{ data }"><span><el-icon><Document /></el-icon> {{ data.name }} <small v-if="data.size">{{ data.size }} B</small></span></template>
                </el-tree>
              </LayoutFormGridItem>
              <LayoutFormGridItem>
                <h3>Manifest</h3>
                <pre class="skill-package__manifest">{{ JSON.stringify(displayManifest, null, 2) }}</pre>
              </LayoutFormGridItem>
              <LayoutFormGridItem v-if="inspection" span="full">
                <h3>风险与兼容性</h3>
                <el-table :data="inspectionRisks" border empty-text="未发现风险项">
                  <el-table-column prop="severity" label="级别" width="100" />
                  <el-table-column prop="path" label="路径" min-width="180" />
                  <el-table-column prop="message" label="说明" min-width="300" />
                </el-table>
              </LayoutFormGridItem>
            </LayoutFormGrid>
          </template>
        </el-tab-pane>
      </el-tabs>
    </el-form>
  </ManagementEditorShell>
</template>

<style scoped>
.skill-package {
  display: grid;
  gap: var(--app-space-4);
}

.skill-package__actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--app-space-2);
}

.skill-package__report {
  margin-top: var(--app-space-4);
}

.skill-package__manifest {
  max-height: 360px;
  margin: 0;
  padding: var(--app-space-4);
  overflow: auto;
  border: 1px solid var(--system-border);
  border-radius: var(--app-radius-lg);
  background: var(--system-surface-muted);
  color: var(--system-text);
}

:deep(.el-select) {
  width: 100%;
}
</style>
