import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createAiFlowNode,
  createAiFlowSkill,
  createAiFlowWorkflow,
  deleteAiFlowNode,
  deleteAiFlowSkill,
  deleteAiFlowWorkflow,
  getAiFlowOverview,
  searchAiFlowNodes,
  searchAiFlowSkills,
  searchAiFlowWorkflows,
  updateAiFlowNode,
  updateAiFlowSkill,
  updateAiFlowWorkflow
} from '../../../../../api/aiFlow'
import { aiFlowSectionTabs } from '../data/ai-flow'
import { showPopup } from '../../../../../utils/popup'

const SECTION_META_MAP = {
  workflow: {
    eyebrow: '流程类型列表',
    title: '先按流程分类管理，再进入具体定义',
    countLabel: '个流程类型',
    emptyText: '当前没有流程类型数据。'
  },
  node: {
    eyebrow: '节点类型列表',
    title: '先看节点能力分类，再进入具体流程',
    countLabel: '类节点配置',
    emptyText: '当前没有节点类型数据。'
  },
  skill: {
    eyebrow: 'Skill挂载列表',
    title: '先看 Skill 分类，再进入挂载定义',
    countLabel: '类Skill配置',
    emptyText: '当前没有 Skill 分类数据。'
  }
}

export function useAiFlowPage() {
  const route = useRoute()
  const router = useRouter()

  const loading = ref(false)
  const errorMessage = ref('')
  const overview = ref({
    workflows: [],
    nodes: [],
    skills: []
  })
  const workflowEntities = ref([])
  const nodeEntities = ref([])
  const skillEntities = ref([])

  const dialogState = reactive({
    visible: false,
    entityType: 'workflow',
    mode: 'create',
    saving: false,
    error: '',
    form: createEmptyForm('workflow')
  })
  const confirmState = reactive({
    visible: false,
    entityType: 'workflow',
    deleting: false,
    title: '',
    targetId: null
  })
  const activeSection = computed(() => {
    const section = route.query.section
    return typeof section === 'string' && aiFlowSectionTabs.some(item => item.key === section) ? section : 'workflow'
  })

  const sectionMeta = computed(() => {
    const key = activeSection.value
    const base = SECTION_META_MAP[key] || SECTION_META_MAP.workflow
    return {
      ...base,
      rows: overview.value[`${key}s`] || []
    }
  })

  onMounted(() => {
    loadOverview()
  })

  function switchSection(sectionKey) {
    router.replace({
      path: route.path,
      query: {
        ...route.query,
        section: sectionKey
      }
    })
  }

  async function loadOverview() {
    loading.value = true
    errorMessage.value = ''
    try {
      const [overviewPayload, workflowPayload, nodePayload, skillPayload] = await Promise.all([
        getAiFlowOverview(),
        searchAiFlowWorkflows({ page: 1, size: 500 }),
        searchAiFlowNodes({ page: 1, size: 500 }),
        searchAiFlowSkills({ page: 1, size: 500 })
      ])
      overview.value = {
        workflows: Array.isArray(overviewPayload?.workflows) ? overviewPayload.workflows : [],
        nodes: Array.isArray(overviewPayload?.nodes) ? overviewPayload.nodes : [],
        skills: Array.isArray(overviewPayload?.skills) ? overviewPayload.skills : []
      }
      workflowEntities.value = unwrapListPayload(workflowPayload)
      nodeEntities.value = unwrapListPayload(nodePayload)
      skillEntities.value = unwrapListPayload(skillPayload)
    } catch (error) {
      errorMessage.value = error.message || 'AI 流程配置加载失败'
      overview.value = { workflows: [], nodes: [], skills: [] }
      workflowEntities.value = []
      nodeEntities.value = []
      skillEntities.value = []
    } finally {
      loading.value = false
    }
  }

  function openCreateDialog(entityType) {
    dialogState.visible = true
    dialogState.entityType = entityType
    dialogState.mode = 'create'
    dialogState.saving = false
    dialogState.error = ''
    dialogState.form = createEmptyForm(entityType)
  }

  function openEditDialog(entityType, item) {
    const entity = findEntity(entityType, item.id)
    if (!entity) {
      showPopup.warning('未找到对应配置实体')
      return
    }
    dialogState.visible = true
    dialogState.entityType = entityType
    dialogState.mode = 'edit'
    dialogState.saving = false
    dialogState.error = ''
    dialogState.form = createFormFromEntity(entityType, entity)
  }

  function closeDialog() {
    dialogState.visible = false
  }

  function openDeleteConfirm(entityType, item) {
    confirmState.visible = true
    confirmState.entityType = entityType
    confirmState.deleting = false
    confirmState.title = item.name || item.code || '-'
    confirmState.targetId = item.id
  }

  function closeDeleteConfirm() {
    confirmState.visible = false
  }

  async function submitDialog() {
    dialogState.error = validateDialogForm(dialogState.entityType, dialogState.form)
    if (dialogState.error) {
      return
    }
    dialogState.saving = true
    try {
      const entityType = dialogState.entityType
      const payload = buildDialogPayload(entityType, dialogState.form)
      if (entityType === 'workflow') {
        if (dialogState.mode === 'create') {
          await createAiFlowWorkflow(payload)
        } else {
          await updateAiFlowWorkflow(dialogState.form.id, payload)
        }
      } else if (entityType === 'node') {
        if (dialogState.mode === 'create') {
          await createAiFlowNode(payload)
        } else {
          await updateAiFlowNode(dialogState.form.id, payload)
        }
      } else if (dialogState.mode === 'create') {
        await createAiFlowSkill(payload)
      } else {
        await updateAiFlowSkill(dialogState.form.id, payload)
      }
      closeDialog()
      await loadOverview()
      showPopup.success(dialogState.mode === 'create' ? '新增成功' : '更新成功')
    } catch (error) {
      dialogState.error = error.message || '保存失败'
    } finally {
      dialogState.saving = false
    }
  }

  async function submitDeleteConfirm() {
    if (!confirmState.targetId) {
      return
    }
    confirmState.deleting = true
    try {
      if (confirmState.entityType === 'workflow') {
        await deleteAiFlowWorkflow(confirmState.targetId)
      } else if (confirmState.entityType === 'node') {
        await deleteAiFlowNode(confirmState.targetId)
      } else {
        await deleteAiFlowSkill(confirmState.targetId)
      }
      closeDeleteConfirm()
      await loadOverview()
      showPopup.success('删除成功')
    } catch (error) {
      showPopup.warning(error.message || '删除失败')
    } finally {
      confirmState.deleting = false
    }
  }

  function findEntity(entityType, id) {
    const list = entityType === 'workflow' ? workflowEntities.value : entityType === 'node' ? nodeEntities.value : skillEntities.value
    return list.find(item => item.id === id) || null
  }

  return {
    sectionTabs: aiFlowSectionTabs,
    activeSection,
    sectionMeta,
    loading,
    errorMessage,
    dialogState,
    confirmState,
    switchSection,
    openCreateDialog,
    openEditDialog,
    closeDialog,
    submitDialog,
    openDeleteConfirm,
    closeDeleteConfirm,
    submitDeleteConfirm,
    loadOverview
  }
}

function unwrapListPayload(payload) {
  if (Array.isArray(payload)) {
    return payload
  }
  return Array.isArray(payload?.list) ? payload.list : []
}

function createEmptyForm(entityType) {
  if (entityType === 'workflow') {
    return {
      id: null,
      key: '',
      code: '',
      name: '',
      type: '',
      scene: '',
      enabled: true,
      tagsText: ''
    }
  }
  if (entityType === 'node') {
    return {
      id: null,
      code: '',
      name: '',
      type: '',
      summary: '',
      enabled: true,
      executeMode: 'SERIAL'
    }
  }
  return {
    id: null,
    code: '',
    name: '',
    type: '',
    summary: '',
    enabled: true,
    supportedPhasesText: 'BEFORE_EXECUTE'
  }
}

function createFormFromEntity(entityType, entity) {
  if (entityType === 'workflow') {
    return {
      id: entity.id,
      key: entity.config?.routeKey || entity.code || '',
      code: entity.code || '',
      name: entity.name || '',
      type: entity.type || '',
      scene: entity.config?.sceneDesc || '',
      enabled: entity.enabled !== false,
      tagsText: Array.isArray(entity.config?.tags) ? entity.config.tags.join(', ') : ''
    }
  }
  if (entityType === 'node') {
    return {
      id: entity.id,
      code: entity.code || '',
      name: entity.name || '',
      type: entity.type || '',
      summary: entity.config?.summary || '',
      enabled: entity.enabled !== false,
      executeMode: entity.config?.executeMode || 'SERIAL'
    }
  }
  return {
    id: entity.id,
    code: entity.code || '',
    name: entity.name || '',
    type: entity.type || '',
    summary: entity.config?.summary || '',
    enabled: entity.enabled !== false,
    supportedPhasesText: Array.isArray(entity.config?.supportedPhases) ? entity.config.supportedPhases.join(', ') : ''
  }
}

function validateDialogForm(entityType, form) {
  if (!form.code.trim()) {
    return '编码不能为空'
  }
  if (!form.name.trim()) {
    return '名称不能为空'
  }
  if (entityType === 'workflow' && !form.key.trim()) {
    return '流程路由 Key 不能为空'
  }
  return ''
}

function buildDialogPayload(entityType, form) {
  if (!form.code.trim()) {
    throw new Error('编码不能为空')
  }
  if (!form.name.trim()) {
    throw new Error('名称不能为空')
  }
  if (entityType === 'workflow') {
    if (!form.key.trim()) {
      throw new Error('流程路由 Key 不能为空')
    }
    return {
      id: form.id,
      key: form.key.trim(),
      code: form.code.trim(),
      name: form.name.trim(),
      type: emptyToUndefined(form.type),
      enabled: form.enabled,
      scene: emptyToUndefined(form.scene),
      tags: parseCommaValues(form.tagsText)
    }
  }
  if (entityType === 'node') {
    return {
      id: form.id,
      code: form.code.trim(),
      name: form.name.trim(),
      type: emptyToUndefined(form.type),
      enabled: form.enabled,
      config: {
        summary: emptyToUndefined(form.summary),
        executeMode: form.executeMode || 'SERIAL',
        inputDefinitions: [],
        configItems: [],
        outputDefinitions: [],
        ext: {}
      }
    }
  }
  return {
    id: form.id,
    code: form.code.trim(),
    name: form.name.trim(),
    type: emptyToUndefined(form.type),
    enabled: form.enabled,
    config: {
      summary: emptyToUndefined(form.summary),
      supportedPhases: parseCommaValues(form.supportedPhasesText),
      defaultOptions: {},
      ext: {}
    }
  }
}

function emptyToUndefined(value) {
  return value && String(value).trim() ? String(value).trim() : undefined
}

function parseCommaValues(value) {
  return String(value || '')
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)
}
