import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getWorkflowTypeByKey } from '../../data/ai-flow'
import { cloneData, createNodeTemplateCatalog, createSkillTemplateCatalog, createWorkflowSeed } from '../../data/ai-flow/detail'

const AI_FLOW_LIST_PATH = '/settings/system/ai-flow'
const workflowNodeState = reactive({})

export function useAiFlowDetailPage() {
  const route = useRoute()
  const router = useRouter()

  const workflow = computed(() => getWorkflowTypeByKey(route.params.workflowKey))
  const currentWorkflowKey = computed(() => route.params.workflowKey || 'query')

  const nodeDefinitions = computed(() => ensureWorkflowState(currentWorkflowKey.value))
  const selectedNodeKey = ref('')
  const selectedNode = computed(() => {
    const list = nodeDefinitions.value
    return list.find(item => item.key === selectedNodeKey.value) || list[0] || null
  })
  const selectedNodeConfigItems = computed(() => selectedNode.value?.configItems || [])
  const selectedNodeSkillItems = computed(() => selectedNode.value?.skillItems || [])
  const nodeTemplateCatalog = computed(() => createNodeTemplateCatalog(currentWorkflowKey.value))
  const skillTemplateCatalog = computed(() => createSkillTemplateCatalog(currentWorkflowKey.value, selectedNode.value))

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
    currentWorkflowKey,
    workflowKey => {
      const list = ensureWorkflowState(workflowKey)
      selectedNodeKey.value = list[0]?.key || ''
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

  function ensureWorkflowState(workflowKey) {
    if (!workflowNodeState[workflowKey]) {
      workflowNodeState[workflowKey] = cloneData(createWorkflowSeed(workflowKey))
    }
    return workflowNodeState[workflowKey]
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

  function buildEmptyForm(entityType) {
    if (entityType === 'node') {
      return {
        templateKey: '',
        key: '',
        name: '',
        type: '',
        status: '启用',
        mode: '串行',
        phase: '',
        summary: ''
      }
    }

    if (entityType === 'config') {
      return {
        templateKey: '',
        key: '',
        name: '',
        type: '提示消息',
        status: '启用',
        mode: '',
        phase: '',
        summary: ''
      }
    }

    return {
      templateKey: '',
      key: '',
      name: '',
      type: '',
      status: '可扩展',
      mode: '',
      phase: 'BEFORE_EXECUTE',
      summary: ''
    }
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
    if (!editorState.form.mode) {
      editorState.form.mode = template.mode || '串行'
    }
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
    if (!editorState.form.phase) {
      editorState.form.phase = template.phase || 'BEFORE_EXECUTE'
    }
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
            phase: '',
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
    applyForm(
      item
        ? {
            templateKey: entityType === 'skill' ? item.key : '',
            key: item.key,
            name: item.name,
            type: entityType === 'config' ? item.type : '',
            status: item.status,
            mode: '',
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

  function closeEditor() {
    editorState.visible = false
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
        : [
            { label: '所属节点', value: selectedNode.value?.name || '-' },
            { label: 'Skill Key', value: item.key },
            { label: '执行阶段', value: item.phase },
            { label: '挂载状态', value: item.status }
          ]

    detailState.visible = true
    detailState.entityType = entityType
    detailState.title = item.name
    detailState.summary = item.summary
    detailState.fields = fields
  }

  function closeDetail() {
    detailState.visible = false
  }

  function moveNode(index, direction) {
    const targetIndex = direction === 'up' ? index - 1 : index + 1
    if (targetIndex < 0 || targetIndex >= nodeDefinitions.value.length) {
      return
    }
    const list = nodeDefinitions.value
    const [item] = list.splice(index, 1)
    list.splice(targetIndex, 0, item)
    selectedNodeKey.value = item.key
    showToast(`节点已${direction === 'up' ? '上移' : '下移'}`)
  }

  function toggleNodeStatus(node) {
    node.status = node.status === '启用' ? '停用' : '启用'
    showToast(`${node.name} 已${node.status}`)
  }

  function removeItem(entityType, item) {
    if (!selectedNode.value) {
      return
    }
    confirmState.visible = true
    confirmState.entityType = entityType
    confirmState.title = item.name
    confirmState.itemKey = item.key
  }

  function closeConfirm() {
    confirmState.visible = false
  }

  function confirmRemoveItem() {
    if (!selectedNode.value) {
      return
    }
    const list = confirmState.entityType === 'config' ? selectedNode.value.configItems : selectedNode.value.skillItems
    const index = list.findIndex(current => current.key === confirmState.itemKey)
    if (index >= 0) {
      list.splice(index, 1)
      showToast(`${confirmState.entityType === 'config' ? '配置项' : 'Skill'}已删除`)
    }
    closeConfirm()
  }

  function submitEditor() {
    const form = editorState.form
    const key = form.key.trim()
    const name = form.name.trim()
    const summary = form.summary.trim()

    if (!key || !name || !summary) {
      showToast('Key、名称和描述不能为空', 'warn')
      return
    }

    if (editorState.entityType === 'node') {
      const template = selectedNodeTemplate.value
      if (!template) {
        showToast('请先选择节点模板', 'warn')
        return
      }
      const list = nodeDefinitions.value
      const duplicated = list.some(item => item.key === key && item.key !== editorState.originalKey)
      if (duplicated) {
        showToast('节点 Key 不能重复', 'warn')
        return
      }

      if (editorState.mode === 'create') {
        list.push({
          key,
          name,
          type: form.type.trim() || '未分类',
          status: form.status || '启用',
          mode: form.mode || '串行',
          summary,
          configItems: cloneData(template.configItems || []),
          skillItems: cloneData(template.skillItems || [])
        })
        selectedNodeKey.value = key
        showToast('节点已新增')
      } else {
        const target = list.find(item => item.key === editorState.originalKey)
        if (!target) {
          return
        }
        target.key = key
        target.name = name
        target.type = form.type.trim() || '未分类'
        target.status = form.status || '启用'
        target.mode = form.mode || '串行'
        target.summary = summary
        target.configItems = cloneData(template.configItems || [])
        target.skillItems = cloneData(template.skillItems || [])
        selectedNodeKey.value = key
        showToast('节点已更新')
      }

      closeEditor()
      return
    }

    const node = selectedNode.value
    if (!node) {
      return
    }

    const list = editorState.entityType === 'config' ? node.configItems : node.skillItems
    const duplicated = list.some(item => item.key === key && item.key !== editorState.originalKey)
    if (duplicated) {
      showToast(`${editorState.entityType === 'config' ? '配置项' : 'Skill'} Key 不能重复`, 'warn')
      return
    }

    if (editorState.entityType === 'skill' && !selectedSkillTemplate.value) {
      showToast('请先选择 Skill 模板', 'warn')
      return
    }

    const payload =
      editorState.entityType === 'config'
        ? {
            key,
            name,
            type: form.type.trim() || '提示消息',
            status: form.status || '启用',
            summary
          }
        : {
            key,
            name,
            phase: form.phase || 'BEFORE_EXECUTE',
            status: form.status || '可扩展',
            summary
          }

    if (editorState.mode === 'create') {
      list.push(payload)
      showToast(`${editorState.entityType === 'config' ? '配置项' : 'Skill'}已新增`)
    } else {
      const target = list.find(item => item.key === editorState.originalKey)
      if (!target) {
        return
      }
      Object.assign(target, payload)
      showToast(`${editorState.entityType === 'config' ? '配置项' : 'Skill'}已更新`)
    }

    closeEditor()
  }

  return {
    workflow,
    nodeDefinitions,
    selectedNode,
    selectedNodeKey,
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
