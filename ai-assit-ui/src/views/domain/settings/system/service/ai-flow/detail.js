import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createAiFlowConfigNode,
  createAiFlowConfigNodeSkill,
  deleteAiFlowConfigNode,
  deleteAiFlowConfigNodeSkill,
  getAiFlowDetail,
  searchAiFlowNodes,
  searchAiFlowSkills,
  updateAiFlowConfigNode,
  updateAiFlowConfigNodeSkill
} from '../../../../../../api/aiFlow'

const AI_FLOW_LIST_PATH = '/settings/system/ai-flow'

export function useAiFlowDetailPage() {
  const route = useRoute()
  const router = useRouter()

  const loading = ref(false)
  const errorMessage = ref('')
  const detail = ref(null)
  const nodeCatalog = ref([])
  const skillCatalog = ref([])
  const selectedNodeKey = ref('')

  const workflow = computed(() => {
    if (!detail.value) {
      return null
    }
    return {
      id: detail.value.workflowId,
      key: detail.value.workflowKey,
      name: detail.value.workflowName,
      scene: detail.value.workflowScene,
      status: detail.value.workflowStatus,
      tags: detail.value.workflowTags || []
    }
  })
  const nodeDefinitions = computed(() => detail.value?.nodeDefinitions || [])
  const selectedNode = computed(() => {
    const list = nodeDefinitions.value
    return list.find(item => item.key === selectedNodeKey.value) || list[0] || null
  })
  const selectedNodeInputDefinitions = computed(() => selectedNode.value?.inputDefinitions || [])
  const selectedNodeOutputDefinitions = computed(() => selectedNode.value?.outputDefinitions || [])
  const selectedNodeConfigItems = computed(() => selectedNode.value?.configItems || [])
  const selectedNodeSkillItems = computed(() => selectedNode.value?.skillItems || [])
  const nodeTemplateCatalog = computed(() => nodeCatalog.value)
  const skillTemplateCatalog = computed(() => skillCatalog.value)

  const toastState = reactive({
    visible: false,
    tone: 'success',
    text: ''
  })

  const editorState = reactive({
    visible: false,
    entityType: 'node',
    mode: 'create',
    targetNodeKey: '',
    originalKey: '',
    form: createEmptyForm('node')
  })

  const detailState = reactive({
    visible: false,
    entityType: 'config',
    title: '',
    summary: '',
    fields: []
  })

  const confirmState = reactive({
    visible: false,
    entityType: 'config',
    title: '',
    itemKey: ''
  })

  const editorTitle = computed(() => {
    const labels = {
      node: '节点',
      input: '输入定义',
      output: '输出定义',
      config: '配置项',
      skill: 'Skill'
    }
    const action = editorState.mode === 'create' ? '新增' : '编辑'
    return `${action}${labels[editorState.entityType]}`
  })

  const availableNodeTemplates = computed(() => {
    const existingKeys = new Set(nodeDefinitions.value.map(item => item.key))
    return nodeTemplateCatalog.value.filter(item => item.key === editorState.originalKey || !existingKeys.has(item.key))
  })

  const selectedNodeTemplate = computed(() => {
    if (editorState.entityType !== 'node') {
      return null
    }
    return nodeTemplateCatalog.value.find(item => item.key === editorState.form.templateKey) || null
  })

  const availableSkillTemplates = computed(() => {
    if (editorState.entityType !== 'skill' || !selectedNode.value) {
      return []
    }
    const existingKeys = new Set(selectedNode.value.skillItems.map(item => item.key))
    return skillTemplateCatalog.value.filter(item => item.key === editorState.originalKey || !existingKeys.has(item.key))
  })

  const selectedSkillTemplate = computed(() => {
    if (editorState.entityType !== 'skill') {
      return null
    }
    return skillTemplateCatalog.value.find(item => item.key === editorState.form.templateKey) || null
  })

  watch(
    () => route.params.workflowKey,
    async workflowKey => {
      await loadPage(String(workflowKey || ''))
    },
    { immediate: true }
  )

  watch(nodeDefinitions, list => {
    if (!list.find(item => item.key === selectedNodeKey.value)) {
      selectedNodeKey.value = list[0]?.key || ''
    }
  })

  let toastTimer = null

  onBeforeUnmount(() => {
    clearTimeout(toastTimer)
  })

  async function loadPage(workflowKey, options = {}) {
    if (!workflowKey) {
      detail.value = null
      return
    }
    loading.value = true
    errorMessage.value = ''
    try {
      const [detailPayload, nodePayload, skillPayload] = await Promise.all([
        getAiFlowDetail(workflowKey),
        searchAiFlowNodes({ page: 1, size: 500 }),
        searchAiFlowSkills({ page: 1, size: 500 })
      ])
      detail.value = mapDetailPayload(detailPayload)
      nodeCatalog.value = unwrapListPayload(nodePayload).map(mapNodeTemplate)
      skillCatalog.value = unwrapListPayload(skillPayload).map(mapSkillTemplate)
      if (options.preferNodeKey && detail.value.nodeDefinitions.find(item => item.key === options.preferNodeKey)) {
        selectedNodeKey.value = options.preferNodeKey
      } else if (!detail.value.nodeDefinitions.find(item => item.key === selectedNodeKey.value)) {
        selectedNodeKey.value = detail.value.nodeDefinitions[0]?.key || ''
      }
    } catch (error) {
      errorMessage.value = error.message || '流程详情加载失败'
      detail.value = null
      nodeCatalog.value = []
      skillCatalog.value = []
    } finally {
      loading.value = false
    }
  }

  function showToast(text, tone = 'success') {
    toastState.visible = true
    toastState.tone = tone
    toastState.text = text
    clearTimeout(toastTimer)
    toastTimer = setTimeout(() => {
      toastState.visible = false
    }, 2200)
  }

  function goBack() {
    router.push(AI_FLOW_LIST_PATH)
  }

  function closeEditor() {
    editorState.visible = false
  }

  function closeDetail() {
    detailState.visible = false
  }

  function closeConfirm() {
    confirmState.visible = false
  }

  function buildEmptyForm(entityType) {
    return createEmptyForm(entityType)
  }

  function applyForm(form) {
    editorState.form = form
  }

  function syncNodeFormWithTemplate(templateKey) {
    const template = nodeTemplateCatalog.value.find(item => item.key === templateKey)
    if (!template) {
      return
    }
    editorState.form.templateKey = template.key
    editorState.form.key = template.key
    editorState.form.name = template.name
    editorState.form.type = template.type
    editorState.form.summary = template.summary
    editorState.form.mode = template.mode || 'SERIAL'
  }

  function syncSkillFormWithTemplate(templateKey) {
    const template = skillTemplateCatalog.value.find(item => item.key === templateKey)
    if (!template) {
      return
    }
    editorState.form.templateKey = template.key
    editorState.form.key = template.key
    editorState.form.name = template.name
    editorState.form.summary = template.summary
    editorState.form.phase = template.phase || 'BEFORE_EXECUTE'
  }

  function openNodeEditor(mode, node = null) {
    editorState.visible = true
    editorState.entityType = 'node'
    editorState.mode = mode
    editorState.targetNodeKey = ''
    editorState.originalKey = node?.key || ''
    applyForm(
      node
        ? {
            templateKey: node.key,
            key: node.key,
            name: node.name,
            type: node.type,
            status: node.status,
            mode: node.mode,
            summary: node.summary
          }
        : buildEmptyForm('node')
    )
    if (mode === 'create') {
      const firstTemplate = availableNodeTemplates.value[0]
      if (firstTemplate) {
        syncNodeFormWithTemplate(firstTemplate.key)
      }
    }
  }

  function openItemEditor(entityType, mode, item = null) {
    if (!selectedNode.value) {
      showToast('请先选择节点', 'warn')
      return
    }
    editorState.visible = true
    editorState.entityType = entityType
    editorState.mode = mode
    editorState.targetNodeKey = selectedNode.value.key
    editorState.originalKey = item?.key || ''

    if (entityType === 'input' || entityType === 'output') {
      applyForm(
        item
          ? {
              fieldCode: item.fieldCode,
              fieldName: item.fieldName,
              fieldPath: item.fieldPath,
              dataType: item.dataType,
              required: item.required === true,
              sourceRef: item.sourceRef,
              summary: ''
            }
          : buildEmptyForm(entityType)
      )
      return
    }

    applyForm(
      item
        ? {
            templateKey: entityType === 'skill' ? item.key : '',
            key: item.key,
            name: item.name,
            type: entityType === 'config' ? item.type : '',
            status: item.status,
            phase: entityType === 'skill' ? item.phase : '',
            summary: item.summary
          }
        : buildEmptyForm(entityType)
    )

    if (entityType === 'skill' && mode === 'create') {
      const firstTemplate = availableSkillTemplates.value[0]
      if (firstTemplate) {
        syncSkillFormWithTemplate(firstTemplate.key)
      }
    }
  }

  function openItemDetail(entityType, item) {
    const fields =
      entityType === 'config'
        ? [
            { label: '所属节点', value: selectedNode.value?.name || '-' },
            { label: '配置 Key', value: item.key },
            { label: '配置类型', value: item.type },
            { label: '当前状态', value: item.status }
          ]
        : entityType === 'skill'
          ? [
              { label: '所属节点', value: selectedNode.value?.name || '-' },
              { label: 'Skill Key', value: item.key },
              { label: '执行阶段', value: item.phase },
              { label: '挂载状态', value: item.status }
            ]
          : [
              { label: '所属节点', value: selectedNode.value?.name || '-' },
              { label: '字段编码', value: item.fieldCode },
              { label: '字段路径', value: item.fieldPath || '-' },
              { label: '数据类型', value: item.dataType || '-' },
              { label: '是否必填', value: item.required ? '是' : '否' }
            ]

    detailState.visible = true
    detailState.entityType = entityType
    detailState.title = item.name || item.fieldName || item.fieldCode
    detailState.summary = item.summary || item.sourceRef || '-'
    detailState.fields = fields
  }

  async function moveNode(index, direction) {
    const list = nodeDefinitions.value.slice()
    const targetIndex = direction === 'up' ? index - 1 : index + 1
    if (targetIndex < 0 || targetIndex >= list.length) {
      return
    }
    const [item] = list.splice(index, 1)
    list.splice(targetIndex, 0, item)
    try {
      await persistNodeOrder(list)
      await loadPage(route.params.workflowKey, { preferNodeKey: item.key })
      showToast(`节点已${direction === 'up' ? '上移' : '下移'}`)
    } catch (error) {
      showToast(error.message || '节点顺序更新失败', 'warn')
    }
  }

  async function toggleNodeStatus(node) {
    try {
      await updateAiFlowConfigNode(node.id, buildConfigNodePayload({
        ...node,
        status: node.status === '启用' ? '停用' : '启用'
      }))
      await loadPage(route.params.workflowKey, { preferNodeKey: node.key })
      showToast(`${node.name} 状态已更新`)
    } catch (error) {
      showToast(error.message || '节点状态更新失败', 'warn')
    }
  }

  function removeItem(entityType, item) {
    confirmState.visible = true
    confirmState.entityType = entityType
    confirmState.title = item.name || item.fieldName || item.fieldCode
    confirmState.itemKey = item.key || item.fieldCode
  }

  async function confirmRemoveItem() {
    if (!selectedNode.value) {
      return
    }
    try {
      if (confirmState.entityType === 'skill') {
        const target = selectedNode.value.skillItems.find(item => item.key === confirmState.itemKey)
        if (target?.id) {
          await deleteAiFlowConfigNodeSkill(target.id)
        }
      } else if (confirmState.entityType === 'config') {
        const nextList = selectedNode.value.configItems.filter(item => item.key !== confirmState.itemKey)
        await updateAiFlowConfigNode(selectedNode.value.id, buildConfigNodePayload({
          ...selectedNode.value,
          configItems: nextList
        }))
      } else if (confirmState.entityType === 'input') {
        const nextList = selectedNode.value.inputDefinitions.filter(item => item.fieldCode !== confirmState.itemKey)
        await updateAiFlowConfigNode(selectedNode.value.id, buildConfigNodePayload({
          ...selectedNode.value,
          inputDefinitions: nextList
        }))
      } else if (confirmState.entityType === 'output') {
        const nextList = selectedNode.value.outputDefinitions.filter(item => item.fieldCode !== confirmState.itemKey)
        await updateAiFlowConfigNode(selectedNode.value.id, buildConfigNodePayload({
          ...selectedNode.value,
          outputDefinitions: nextList
        }))
      } else {
        const skillItems = selectedNode.value.skillItems.filter(item => item.id)
        await Promise.all(skillItems.map(item => deleteAiFlowConfigNodeSkill(item.id)))
        await deleteAiFlowConfigNode(selectedNode.value.id)
      }
      closeConfirm()
      await loadPage(route.params.workflowKey, { preferNodeKey: selectedNode.value.key })
      showToast('删除成功')
    } catch (error) {
      showToast(error.message || '删除失败', 'warn')
    }
  }

  async function submitEditor() {
    try {
      if (editorState.entityType === 'node') {
        await submitNodeEditor()
      } else if (editorState.entityType === 'skill') {
        await submitSkillEditor()
      } else if (editorState.entityType === 'config') {
        await submitConfigItemEditor()
      } else {
        await submitFieldDefinitionEditor(editorState.entityType)
      }
      closeEditor()
      await loadPage(route.params.workflowKey, { preferNodeKey: editorState.targetNodeKey || selectedNode.value?.key || editorState.form.key })
    } catch (error) {
      showToast(error.message || '保存失败', 'warn')
    }
  }

  async function submitNodeEditor() {
    const form = editorState.form
    if (editorState.mode === 'create') {
      if (!selectedNodeTemplate.value || !detail.value?.configCode) {
        throw new Error('请选择节点模板')
      }
      const template = selectedNodeTemplate.value
      await createAiFlowConfigNode({
        configCode: detail.value.configCode,
        nodeCode: template.key,
        sort: nodeDefinitions.value.length + 1,
        nextCode: null,
        enabled: form.status !== '停用',
        config: {
          summary: form.summary.trim(),
          executeMode: form.mode || 'SERIAL',
          inputDefinitions: deepClone(template.inputDefinitions),
          configItems: deepClone(template.configItems),
          outputDefinitions: deepClone(template.outputDefinitions),
          options: {},
          ext: {}
        }
      })
      editorState.targetNodeKey = template.key
      showToast('节点已新增')
      return
    }
    const target = selectedNode.value
    if (!target) {
      throw new Error('未找到节点')
    }
    await updateAiFlowConfigNode(target.id, buildConfigNodePayload({
      ...target,
      status: form.status,
      mode: form.mode,
      summary: form.summary
    }))
    editorState.targetNodeKey = target.key
    showToast('节点已更新')
  }

  async function submitSkillEditor() {
    const form = editorState.form
    if (!selectedNode.value) {
      throw new Error('请先选择节点')
    }
    if (editorState.mode === 'create') {
      if (!selectedSkillTemplate.value) {
        throw new Error('请选择 Skill 模板')
      }
      await createAiFlowConfigNodeSkill({
        configCode: detail.value.configCode,
        nodeCode: selectedNode.value.key,
        skillCode: selectedSkillTemplate.value.key,
        phase: form.phase || 'BEFORE_EXECUTE',
        sort: selectedNode.value.skillItems.length + 1,
        enabled: form.status !== '未挂接',
        config: {
          required: false,
          options: {},
          ext: {}
        }
      })
      editorState.targetNodeKey = selectedNode.value.key
      showToast('Skill 已新增')
      return
    }
    const target = selectedNode.value.skillItems.find(item => item.key === editorState.originalKey)
    if (!target?.id) {
      throw new Error('未找到 Skill 挂载')
    }
    await updateAiFlowConfigNodeSkill(target.id, {
      configCode: detail.value.configCode,
      nodeCode: selectedNode.value.key,
      skillCode: target.key,
      phase: form.phase || target.phase || 'BEFORE_EXECUTE',
      sort: selectedNode.value.skillItems.findIndex(item => item.key === target.key) + 1,
      enabled: form.status !== '未挂接',
      config: {
        required: false,
        options: {},
        ext: {}
      }
    })
    editorState.targetNodeKey = selectedNode.value.key
    showToast('Skill 已更新')
  }

  async function submitConfigItemEditor() {
    if (!selectedNode.value) {
      throw new Error('请先选择节点')
    }
    const form = editorState.form
    if (!form.key.trim() || !form.name.trim()) {
      throw new Error('配置项 Key 和名称不能为空')
    }
    const nextList = selectedNode.value.configItems.slice()
    const payload = {
      key: form.key.trim(),
      name: form.name.trim(),
      type: form.type.trim() || 'PROMPT',
      status: form.status || '启用',
      summary: form.summary.trim()
    }
    const index = nextList.findIndex(item => item.key === editorState.originalKey)
    if (editorState.mode === 'create') {
      nextList.push(payload)
    } else if (index >= 0) {
      nextList.splice(index, 1, payload)
    }
    await updateAiFlowConfigNode(selectedNode.value.id, buildConfigNodePayload({
      ...selectedNode.value,
      configItems: nextList
    }))
    editorState.targetNodeKey = selectedNode.value.key
    showToast(`配置项已${editorState.mode === 'create' ? '新增' : '更新'}`)
  }

  async function submitFieldDefinitionEditor(entityType) {
    if (!selectedNode.value) {
      throw new Error('请先选择节点')
    }
    const form = editorState.form
    if (!form.fieldCode.trim() || !form.fieldName.trim()) {
      throw new Error('字段编码和名称不能为空')
    }
    const sourceList = entityType === 'input' ? selectedNode.value.inputDefinitions.slice() : selectedNode.value.outputDefinitions.slice()
    const nextField = {
      fieldCode: form.fieldCode.trim(),
      fieldName: form.fieldName.trim(),
      fieldPath: form.fieldPath.trim(),
      dataType: form.dataType.trim() || 'STRING',
      required: form.required === true,
      sourceRef: form.sourceRef.trim(),
      schema: {},
      ext: {}
    }
    const index = sourceList.findIndex(item => item.fieldCode === editorState.originalKey)
    if (editorState.mode === 'create') {
      sourceList.push(nextField)
    } else if (index >= 0) {
      sourceList.splice(index, 1, nextField)
    }
    await updateAiFlowConfigNode(selectedNode.value.id, buildConfigNodePayload({
      ...selectedNode.value,
      inputDefinitions: entityType === 'input' ? sourceList : selectedNode.value.inputDefinitions,
      outputDefinitions: entityType === 'output' ? sourceList : selectedNode.value.outputDefinitions
    }))
    editorState.targetNodeKey = selectedNode.value.key
    showToast(`字段定义已${editorState.mode === 'create' ? '新增' : '更新'}`)
  }

  return {
    workflow,
    loading,
    errorMessage,
    nodeDefinitions,
    selectedNode,
    selectedNodeKey,
    selectedNodeInputDefinitions,
    selectedNodeOutputDefinitions,
    selectedNodeConfigItems,
    selectedNodeSkillItems,
    toastState,
    editorState,
    detailState,
    confirmState,
    editorTitle,
    availableNodeTemplates,
    selectedNodeTemplate,
    availableSkillTemplates,
    selectedSkillTemplate,
    goBack,
    openNodeEditor,
    openItemEditor,
    syncNodeFormWithTemplate,
    syncSkillFormWithTemplate,
    closeEditor,
    openItemDetail,
    closeDetail,
    moveNode,
    toggleNodeStatus,
    removeItem,
    closeConfirm,
    confirmRemoveItem,
    submitEditor
  }
}

function mapDetailPayload(payload) {
  if (!payload) {
    return null
  }
  return {
    ...payload,
    nodeDefinitions: Array.isArray(payload.nodeDefinitions) ? payload.nodeDefinitions.map(mapNodeDefinition) : []
  }
}

function mapNodeDefinition(item) {
  return {
    ...item,
    mode: item.mode || 'SERIAL',
    inputDefinitions: Array.isArray(item.inputDefinitions) ? item.inputDefinitions : [],
    outputDefinitions: Array.isArray(item.outputDefinitions) ? item.outputDefinitions : [],
    configItems: Array.isArray(item.configItems)
      ? item.configItems.map(configItem => ({
          key: configItem.code,
          name: configItem.name,
          type: configItem.type,
          summary: configItem.summary,
          status: configItem.enabled === false ? '停用' : '启用'
        }))
      : [],
    skillItems: Array.isArray(item.skillItems)
      ? item.skillItems.map(skillItem => ({
          id: skillItem.id,
          key: skillItem.key,
          name: skillItem.name,
          phase: skillItem.phase,
          status: skillItem.status || '已挂接',
          summary: skillItem.summary
        }))
      : []
  }
}

function mapNodeTemplate(item) {
  return {
    id: item.id,
    key: item.code,
    name: item.name,
    type: item.type,
    mode: item.config?.executeMode || 'SERIAL',
    summary: item.config?.summary || '',
    inputDefinitions: Array.isArray(item.config?.inputDefinitions) ? item.config.inputDefinitions : [],
    outputDefinitions: Array.isArray(item.config?.outputDefinitions) ? item.config.outputDefinitions : [],
    configItems: Array.isArray(item.config?.configItems)
      ? item.config.configItems.map(configItem => ({
          key: configItem.code,
          name: configItem.name,
          type: configItem.type,
          summary: configItem.summary,
          status: configItem.enabled === false ? '停用' : '启用'
        }))
      : [],
    skillItems: []
  }
}

function mapSkillTemplate(item) {
  const phases = Array.isArray(item.config?.supportedPhases) ? item.config.supportedPhases : []
  return {
    id: item.id,
    key: item.code,
    name: item.name,
    summary: item.config?.summary || '',
    phase: phases[0] || 'BEFORE_EXECUTE',
    supportedPhases: phases,
    status: item.enabled === false ? '未挂接' : '已挂接'
  }
}

function unwrapListPayload(payload) {
  if (Array.isArray(payload)) {
    return payload
  }
  return Array.isArray(payload?.list) ? payload.list : []
}

function createEmptyForm(entityType) {
  if (entityType === 'node') {
    return {
      templateKey: '',
      key: '',
      name: '',
      type: '',
      status: '启用',
      mode: 'SERIAL',
      summary: ''
    }
  }
  if (entityType === 'config') {
    return {
      key: '',
      name: '',
      type: 'PROMPT',
      status: '启用',
      summary: ''
    }
  }
  if (entityType === 'skill') {
    return {
      templateKey: '',
      key: '',
      name: '',
      status: '已挂接',
      phase: 'BEFORE_EXECUTE',
      summary: ''
    }
  }
  return {
    fieldCode: '',
    fieldName: '',
    fieldPath: '',
    dataType: 'STRING',
    required: false,
    sourceRef: '',
    summary: ''
  }
}

function buildConfigNodePayload(node) {
  return {
    configCode: node.configCode,
    nodeCode: node.nodeCode || node.key,
    sort: node.sort,
    nextCode: node.nextCode,
    enabled: node.status !== '停用',
    config: {
      summary: node.summary || '',
      executeMode: node.mode || 'SERIAL',
      inputDefinitions: Array.isArray(node.inputDefinitions) ? deepClone(node.inputDefinitions) : [],
      configItems: Array.isArray(node.configItems)
        ? node.configItems.map(item => ({
            code: item.key,
            name: item.name,
            type: item.type,
            summary: item.summary,
            enabled: item.status !== '停用',
            ext: {}
          }))
        : [],
      outputDefinitions: Array.isArray(node.outputDefinitions) ? deepClone(node.outputDefinitions) : [],
      options: {},
      ext: {}
    }
  }
}

async function persistNodeOrder(list) {
  await Promise.all(
    list.map((item, index) =>
      updateAiFlowConfigNode(item.id, buildConfigNodePayload({
        ...item,
        sort: index + 1,
        nextCode: list[index + 1]?.key || null
      }))
    )
  )
}

function deepClone(data) {
  return JSON.parse(JSON.stringify(data))
}
