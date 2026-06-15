<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getWorkflowTypeByKey } from '../data/ai-flow'

const route = useRoute()
const router = useRouter()

const workflow = computed(() => getWorkflowTypeByKey(route.params.workflowKey))
const currentWorkflowKey = computed(() => route.params.workflowKey || 'query')

function createWorkflowSeed(workflowKey) {
  if (workflowKey === 'query') {
    return [
      {
        key: 'chat-message',
        name: 'ChatMessageNode',
        type: '会话初始化',
        status: '启用',
        mode: '串行',
        summary: '初始化会话、轮次和用户消息，为后续节点准备完整执行上下文。',
        configItems: [
          { key: 'system-message', name: '系统提示消息', type: '提示消息', summary: '定义进入节点时发送给 AI 的系统级提示内容。', status: '启用' },
          { key: 'user-message-template', name: '用户消息模板', type: '提示消息', summary: '约束当前用户消息和上下文字段的拼装格式。', status: '启用' },
          { key: 'output-schema', name: '返回数据结构', type: '输出结构', summary: '定义 message、session、round 等字段的返回结构。', status: '启用' }
        ],
        skillItems: [
          { key: 'default-pass-through', name: '默认透传', phase: '默认', summary: '当前节点默认不挂接强制 skill，保持消息初始化主路径稳定。', status: '未挂接' },
          { key: 'input-normalize', name: '输入清洗', phase: 'BEFORE_EXECUTE', summary: '在消息入上下文前统一清理空白、格式和异常字符。', status: '可扩展' },
          { key: 'message-preprocess', name: '消息预处理', phase: 'BEFORE_EXECUTE', summary: '在会话初始化前补充额外字段或路由标签。', status: '可扩展' }
        ]
      },
      {
        key: 'query-planning',
        name: 'QueryPlanningNode',
        type: '查询规划',
        status: '启用',
        mode: '串行',
        summary: '解析用户目标并生成结构化查询规划，决定知识检索和 SQL 生成方向。',
        configItems: [
          { key: 'planning-system-prompt', name: '规划提示消息', type: '提示消息', summary: '定义查询规划节点使用的 system prompt。', status: '启用' },
          { key: 'planning-user-template', name: '规划输入模板', type: '提示消息', summary: '定义历史消息、当前问题和补充上下文的组装格式。', status: '启用' },
          { key: 'planning-schema', name: '规划返回数据结构', type: '输出结构', summary: '定义 userGoal、analysisSummary、sqlFocus 等结构字段。', status: '启用' }
        ],
        skillItems: [
          { key: 'business-term-resolve', name: '术语解析', phase: 'BEFORE_EXECUTE', summary: '抽取业务术语并回填给查询规划输入。', status: '已挂接' },
          { key: 'time-range-normalize', name: '时间范围归一化', phase: 'BEFORE_EXECUTE', summary: '将自然语言时间转换成标准化时间范围。', status: '已挂接' },
          { key: 'query-plan-review', name: '查询规划审查', phase: 'REVIEW_OUTPUT', summary: '对规划结果做结构和风险审查。', status: '已挂接' }
        ]
      },
      {
        key: 'knowledge-search',
        name: 'KnowledgeSearchNode',
        type: '知识检索',
        status: '启用',
        mode: '串行',
        summary: '补充知识库口径、模型概览和外部上下文，为 SQL 生成节点提供辅助信息。',
        configItems: [
          { key: 'knowledge-query-template', name: '检索请求模板', type: '提示消息', summary: '定义知识库查询串的拼装规则。', status: '启用' },
          { key: 'knowledge-result-schema', name: '检索返回数据结构', type: '输出结构', summary: '定义知识命中摘要、metadata 和兜底结构。', status: '启用' }
        ],
        skillItems: [
          { key: 'retrieval-rerank', name: '检索重排', phase: 'AFTER_EXECUTE', summary: '对知识检索命中结果进行重排和裁剪。', status: '可扩展' },
          { key: 'term-enrich', name: '术语补全', phase: 'BEFORE_EXECUTE', summary: '在发起知识检索前补齐同义词和口径别名。', status: '可扩展' }
        ]
      },
      {
        key: 'sql-generate',
        name: 'SqlGenerateNode',
        type: 'SQL 生成',
        status: '启用',
        mode: '串行',
        summary: '基于规划结果、知识上下文、SQL 规则和用户偏好生成候选 SQL。',
        configItems: [
          { key: 'sql-system-prompt', name: 'SQL 生成提示消息', type: '提示消息', summary: '定义 SQL 生成节点的 system prompt。', status: '启用' },
          { key: 'sql-user-template', name: 'SQL 输入模板', type: '提示消息', summary: '定义查询规划、知识上下文和偏好配置的拼装格式。', status: '启用' },
          { key: 'sql-output-schema', name: 'SQL 返回数据结构', type: '输出结构', summary: '定义 generatedSql、requestId 和扩展元数据结构。', status: '启用' }
        ],
        skillItems: [
          { key: 'sql-generation-policy', name: 'SQL 生成规范', phase: 'BEFORE_EXECUTE', summary: '注入 SQL 硬约束、软规范和白名单规则。', status: '已挂接' },
          { key: 'user-preference-resolve', name: '用户偏好解析', phase: 'BEFORE_EXECUTE', summary: '提取用户偏好，补充 SQL 生成软约束。', status: '已挂接' }
        ]
      },
      {
        key: 'sql-validate',
        name: 'SqlValidateNode',
        type: 'SQL 校验',
        status: '启用',
        mode: '回跳控制',
        summary: '执行本地安全校验和结构校验，必要时回跳 SQL 生成节点重新出稿。',
        configItems: [
          { key: 'validate-rule-set', name: '校验规则集', type: '规则配置', summary: '定义危险关键字、多语句和只读约束规则。', status: '启用' },
          { key: 'validate-result-schema', name: '校验返回数据结构', type: '输出结构', summary: '定义 validatedSql、validationError 和回跳指令结构。', status: '启用' }
        ],
        skillItems: [
          { key: 'sql-ast-validate', name: 'SQL AST 校验', phase: 'AFTER_EXECUTE', summary: '对生成 SQL 做 AST 级结构校验。', status: '可扩展' },
          { key: 'semantic-check', name: '口径一致性检查', phase: 'REVIEW_OUTPUT', summary: '检查字段、聚合口径和规划结果是否一致。', status: '可扩展' }
        ]
      },
      {
        key: 'sql-execute',
        name: 'SqlExecuteNode',
        type: 'SQL 执行',
        status: '启用',
        mode: '串行',
        summary: '接入预执行结果或统一降级执行结果，收敛最终执行状态。',
        configItems: [
          { key: 'execute-input-template', name: '执行输入模板', type: '提示消息', summary: '定义 validatedSql 和预执行结果的入参格式。', status: '启用' },
          { key: 'execute-result-schema', name: '执行返回数据结构', type: '输出结构', summary: '定义 sqlExecutionStatus、sqlExecutionResult 的结构。', status: '启用' }
        ],
        skillItems: [
          { key: 'execute-audit', name: '执行审计', phase: 'AFTER_EXECUTE', summary: '记录执行耗时、入参摘要和调用来源。', status: '可扩展' },
          { key: 'result-sample', name: '结果采样', phase: 'REVIEW_OUTPUT', summary: '对执行结果做抽样检查和结果摘要。', status: '可扩展' }
        ]
      },
      {
        key: 'render',
        name: 'RenderNode',
        type: '结果渲染',
        status: '启用',
        mode: '串行',
        summary: '汇总规划、SQL 和执行结果，生成最终回答并落库 assistant 消息。',
        configItems: [
          { key: 'render-system-prompt', name: '渲染提示消息', type: '提示消息', summary: '定义最终回答渲染节点使用的 system prompt。', status: '启用' },
          { key: 'render-input-template', name: '渲染输入模板', type: '提示消息', summary: '定义规划结果、SQL 结果和知识上下文的拼装格式。', status: '启用' },
          { key: 'render-output-schema', name: '渲染返回数据结构', type: '输出结构', summary: '定义 renderedAnswer 和最终 artifact 的结构。', status: '启用' }
        ],
        skillItems: [
          { key: 'output-polish', name: '输出润色', phase: 'AFTER_EXECUTE', summary: '对最终回答做语句润色和格式整理。', status: '可扩展' },
          { key: 'answer-review', name: '结果审查', phase: 'REVIEW_OUTPUT', summary: '检查回答是否覆盖关键信息与风险提示。', status: '可扩展' }
        ]
      }
    ]
  }

  if (workflowKey === 'chat') {
    return [
      {
        key: 'session-prepare',
        name: 'SessionPrepare',
        type: '会话准备',
        status: '规划中',
        mode: '串行',
        summary: '准备通用对话上下文。',
        configItems: [{ key: 'session-settings', name: '会话配置', type: '规则配置', summary: '维护基础会话参数、上下文窗口和初始化策略。', status: '规划中' }],
        skillItems: [{ key: 'session-default-skill', name: '上下文兜底', phase: '默认', summary: '后续在会话准备阶段统一挂接基础 skill。', status: '待补充' }]
      },
      {
        key: 'prompt-build',
        name: 'PromptBuild',
        type: '提示构建',
        status: '规划中',
        mode: '串行',
        summary: '构建通用对话 prompt。',
        configItems: [{ key: 'prompt-template', name: 'Prompt 模板', type: '提示消息', summary: '定义系统提示词、上下文拼装和输出结构。', status: '规划中' }],
        skillItems: [{ key: 'prompt-polish', name: 'Prompt 优化', phase: 'BEFORE_EXECUTE', summary: '补充角色信息和输出约束。', status: '待补充' }]
      },
      {
        key: 'model-call',
        name: 'ModelCall',
        type: '模型调用',
        status: '规划中',
        mode: '串行',
        summary: '执行模型调用。',
        configItems: [{ key: 'model-route', name: '模型路由', type: '规则配置', summary: '维护模型选择、重试和降级策略。', status: '规划中' }],
        skillItems: [{ key: 'model-review', name: '模型结果审查', phase: 'REVIEW_OUTPUT', summary: '审查模型输出结构和敏感内容。', status: '待补充' }]
      },
      {
        key: 'render',
        name: 'Render',
        type: '结果渲染',
        status: '规划中',
        mode: '串行',
        summary: '渲染通用对话结果。',
        configItems: [{ key: 'render-template', name: '输出模板', type: '输出结构', summary: '统一回复样式、引用信息和推荐项结构。', status: '规划中' }],
        skillItems: [{ key: 'answer-polish', name: '结果润色', phase: 'AFTER_EXECUTE', summary: '统一输出口吻、段落结构和结论风格。', status: '待补充' }]
      }
    ]
  }

  if (workflowKey === 'app') {
    return [
      {
        key: 'intent-route',
        name: 'IntentRoute',
        type: '意图路由',
        status: '规划中',
        mode: '串行',
        summary: '执行应用流程意图路由。',
        configItems: [{ key: 'intent-rules', name: '路由规则', type: '规则配置', summary: '定义应用入口意图识别和分流条件。', status: '规划中' }],
        skillItems: [{ key: 'intent-normalize', name: '意图归一化', phase: 'BEFORE_EXECUTE', summary: '统一用户输入中的业务意图描述。', status: '待补充' }]
      },
      {
        key: 'tool-plan',
        name: 'ToolPlan',
        type: '工具规划',
        status: '规划中',
        mode: '串行',
        summary: '规划工具调用顺序。',
        configItems: [{ key: 'tool-graph', name: '工具编排', type: '规则配置', summary: '定义工具依赖图、串并行策略和超时规则。', status: '规划中' }],
        skillItems: [{ key: 'tool-risk-review', name: '工具风险审查', phase: 'REVIEW_OUTPUT', summary: '对工具编排结果做权限和成本审查。', status: '待补充' }]
      },
      {
        key: 'skill-execute',
        name: 'SkillExecute',
        type: '技能执行',
        status: '规划中',
        mode: '并行/串行',
        summary: '执行技能节点。',
        configItems: [{ key: 'skill-runtime', name: '技能运行策略', type: '规则配置', summary: '定义技能超时、重试和日志追踪策略。', status: '规划中' }],
        skillItems: [{ key: 'skill-router', name: '技能路由', phase: 'BEFORE_EXECUTE', summary: '根据上下文选择待执行技能。', status: '待补充' }]
      },
      {
        key: 'result-assemble',
        name: 'ResultAssemble',
        type: '结果组装',
        status: '规划中',
        mode: '串行',
        summary: '组装应用流程结果。',
        configItems: [{ key: 'result-template', name: '结果模板', type: '输出结构', summary: '定义工具结果聚合结构和呈现顺序。', status: '规划中' }],
        skillItems: [{ key: 'result-merge', name: '结果合并', phase: 'AFTER_EXECUTE', summary: '对多技能返回结果做统一聚合。', status: '待补充' }]
      },
      {
        key: 'render',
        name: 'Render',
        type: '结果渲染',
        status: '规划中',
        mode: '串行',
        summary: '渲染应用结果。',
        configItems: [{ key: 'render-schema', name: '渲染模板', type: '输出结构', summary: '定义最终返回卡片、摘要和行动按钮结构。', status: '规划中' }],
        skillItems: [{ key: 'render-polish', name: '输出润色', phase: 'AFTER_EXECUTE', summary: '统一最终渲染样式和可读性。', status: '待补充' }]
      }
    ]
  }

  return [
    {
      key: 'definition-load',
      name: 'DefinitionLoad',
      type: '流程读取',
      status: '待补充',
      mode: '串行',
      summary: '读取流程定义。',
      configItems: [{ key: 'definition-source', name: '流程来源', type: '规则配置', summary: '维护流程定义加载来源和版本路由。', status: '待补充' }],
      skillItems: [{ key: 'definition-review', name: '流程读取审查', phase: 'REVIEW_OUTPUT', summary: '校验流程定义完整性和可回放性。', status: '待补充' }]
    },
    {
      key: 'artifact-replay',
      name: 'ArtifactReplay',
      type: '产物回放',
      status: '待补充',
      mode: '串行',
      summary: '回放历史产物。',
      configItems: [{ key: 'artifact-source', name: '回放来源', type: '规则配置', summary: '定义产物回放的数据源、时间范围和过滤条件。', status: '待补充' }],
      skillItems: [{ key: 'artifact-compare', name: '回放比对', phase: 'AFTER_EXECUTE', summary: '对回放产物和基线结果进行差异分析。', status: '待补充' }]
    },
    {
      key: 'review',
      name: 'Review',
      type: '流程审查',
      status: '待补充',
      mode: '串行',
      summary: '执行流程审查。',
      configItems: [{ key: 'review-rules', name: '审查规则', type: '规则配置', summary: '维护流程审查维度、风险等级和拦截条件。', status: '待补充' }],
      skillItems: [{ key: 'review-summary', name: '审查总结', phase: 'REVIEW_OUTPUT', summary: '生成流程审查总结和整改建议。', status: '待补充' }]
    },
    {
      key: 'report',
      name: 'Report',
      type: '结果报告',
      status: '待补充',
      mode: '串行',
      summary: '输出审查报告。',
      configItems: [{ key: 'report-template', name: '报告模板', type: '输出结构', summary: '定义回放和审查报告的章节结构。', status: '待补充' }],
      skillItems: [{ key: 'report-polish', name: '报告润色', phase: 'AFTER_EXECUTE', summary: '统一最终报告口径和摘要风格。', status: '待补充' }]
    }
  ]
}

function cloneData(data) {
  return JSON.parse(JSON.stringify(data))
}

const workflowNodeState = reactive({})

function ensureWorkflowState(workflowKey) {
  if (!workflowNodeState[workflowKey]) {
    workflowNodeState[workflowKey] = cloneData(createWorkflowSeed(workflowKey))
  }
  return workflowNodeState[workflowKey]
}

const nodeDefinitions = computed(() => ensureWorkflowState(currentWorkflowKey.value))

const selectedNodeKey = ref('')

const selectedNode = computed(() => {
  const list = nodeDefinitions.value
  return list.find(item => item.key === selectedNodeKey.value) || list[0] || null
})

const selectedNodeConfigItems = computed(() => selectedNode.value?.configItems || [])

const selectedNodeSkillItems = computed(() => selectedNode.value?.skillItems || [])

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

const toastState = reactive({
  visible: false,
  tone: 'success',
  text: ''
})

let toastTimer = null

function showToast(text, tone = 'success') {
  toastState.visible = true
  toastState.tone = tone
  toastState.text = text
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastState.visible = false
  }, 2200)
}

const editorState = reactive({
  visible: false,
  entityType: 'node',
  mode: 'create',
  targetNodeKey: '',
  originalKey: '',
  form: {
    key: '',
    name: '',
    type: '',
    status: '',
    mode: '',
    phase: '',
    summary: ''
  }
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

function buildEmptyForm(entityType) {
  if (entityType === 'node') {
    return {
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

function openNodeEditor(mode, node = null) {
  editorState.visible = true
  editorState.entityType = 'node'
  editorState.mode = mode
  editorState.targetNodeKey = ''
  editorState.originalKey = node?.key || ''
  applyForm(
    node
      ? {
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
        configItems: [],
        skillItems: []
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
</script>

<template>
  <div class="ai-flow-detail-page">
    <template v-if="workflow">
      <header class="content-head">
        <div class="content-head-main">
          <div>
            <p class="crumb">系统设置 / AI流程配置 / {{ workflow.name }}</p>
            <h1>{{ workflow.name }}</h1>
            <p class="section-desc">{{ workflow.scene }}</p>
          </div>
          <button type="button" class="back-link" @click="router.push('/settings/system/ai-flow')">返回流程列表</button>
        </div>
      </header>

      <section class="detail-grid">
        <article class="detail-card detail-card-wide">
          <div class="section-head">
            <div>
              <p class="eyebrow">配置分区</p>
              <h3>节点定义</h3>
            </div>
            <button type="button" class="mini-action primary" @click="openNodeEditor('create')">新增节点</button>
          </div>
          <p class="section-desc-inline">这里按子列表维护节点顺序、跳转关系、启停状态和节点类型。</p>

          <div class="node-list">
            <article
              v-for="(node, index) in nodeDefinitions"
              :key="node.key"
              class="node-row"
              :class="{ active: selectedNode?.key === node.key }"
              @click="selectedNodeKey = node.key"
            >
              <div class="node-index">{{ index + 1 }}</div>

              <div class="node-main">
                <div class="node-title-row">
                  <strong>{{ node.name }}</strong>
                  <span class="node-type">{{ node.type }}</span>
                </div>
                <div class="node-meta">
                  <span>节点 Key：{{ node.key }}</span>
                  <span>执行方式：{{ node.mode }}</span>
                  <span>状态：{{ node.status }}</span>
                </div>
              </div>

              <div class="node-actions">
                <button type="button" class="mini-action" @click.stop="openNodeEditor('edit', node)">编辑</button>
                <button type="button" class="mini-action" @click.stop="toggleNodeStatus(node)">{{ node.status === '启用' ? '停用' : '启用' }}</button>
                <div class="node-move-actions">
                  <button type="button" class="mini-action icon" title="上移" :disabled="index === 0" @click.stop="moveNode(index, 'up')">↑</button>
                  <button type="button" class="mini-action icon" title="下移" :disabled="index === nodeDefinitions.length - 1" @click.stop="moveNode(index, 'down')">↓</button>
                </div>
              </div>
            </article>
          </div>
        </article>

        <div class="detail-side-column">
          <article class="detail-card detail-card-scroll-section">
            <p class="eyebrow">节点功能描述</p>
            <h3>{{ selectedNode?.name }}</h3>
            <p>{{ selectedNode?.summary }}</p>
          </article>

          <article class="detail-card detail-card-scroll-section">
            <div class="section-head">
              <div>
                <p class="eyebrow">节点配置</p>
                <h3>配置项列表</h3>
              </div>
              <button type="button" class="mini-action" @click="openItemEditor('config', 'create')">新增配置</button>
            </div>
            <p class="section-desc-inline">核心维护提示 AI 的消息模板、返回数据结构以及规则类配置。</p>

            <div class="config-list">
              <article v-for="item in selectedNodeConfigItems" :key="item.key" class="config-row">
                <div class="config-main">
                  <div class="config-title-row">
                    <strong>{{ item.name }}</strong>
                    <span class="config-type">{{ item.type }}</span>
                    <span class="config-status">{{ item.status }}</span>
                  </div>
                  <p>{{ item.summary }}</p>
                </div>

                <div class="config-actions">
                  <button type="button" class="mini-action" @click="openItemDetail('config', item)">查看详情</button>
                  <button type="button" class="mini-action" @click="openItemEditor('config', 'edit', item)">编辑</button>
                  <button type="button" class="mini-action danger" @click="removeItem('config', item)">删除</button>
                </div>
              </article>
              <div v-if="!selectedNodeConfigItems.length" class="list-empty">当前节点还没有配置项，先新增一条配置。</div>
            </div>
          </article>

          <article class="detail-card detail-card-scroll-section">
            <div class="section-head">
              <div>
                <p class="eyebrow">Skill 配置</p>
                <h3>挂载技能列表</h3>
              </div>
              <button type="button" class="mini-action" @click="openItemEditor('skill', 'create')">新增 Skill</button>
            </div>
            <p class="section-desc-inline">按执行阶段维护 skill 挂载，支持查看详情、编辑和删除。</p>

            <div class="config-list">
              <article v-for="item in selectedNodeSkillItems" :key="item.key" class="config-row">
                <div class="config-main">
                  <div class="config-title-row">
                    <strong>{{ item.name }}</strong>
                    <span class="config-type">{{ item.phase }}</span>
                    <span class="config-status" :class="{ draft: item.status !== '已挂接' }">{{ item.status }}</span>
                  </div>
                  <p>{{ item.summary }}</p>
                </div>

                <div class="config-actions">
                  <button type="button" class="mini-action" @click="openItemDetail('skill', item)">查看详情</button>
                  <button type="button" class="mini-action" @click="openItemEditor('skill', 'edit', item)">编辑</button>
                  <button type="button" class="mini-action danger" @click="removeItem('skill', item)">删除</button>
                </div>
              </article>
              <div v-if="!selectedNodeSkillItems.length" class="list-empty">当前节点还没有 Skill 挂载，先新增一个 Skill。</div>
            </div>
          </article>
        </div>
      </section>

    </template>

    <section v-else class="detail-empty">
      <h2>流程不存在</h2>
      <p>未找到对应的流程类型，请返回流程列表重新选择。</p>
      <button type="button" class="back-link" @click="router.push('/settings/system/ai-flow')">返回流程列表</button>
    </section>

    <transition name="fade">
      <div v-if="toastState.visible" class="toast" :class="toastState.tone">
        {{ toastState.text }}
      </div>
    </transition>

    <div v-if="editorState.visible" class="floating-mask" @click.self="closeEditor">
      <section class="floating-panel">
        <div class="floating-head">
          <div>
            <p class="eyebrow">本地交互</p>
            <h3>{{ editorTitle }}</h3>
          </div>
          <button type="button" class="ghost-action" @click="closeEditor">关闭</button>
        </div>

        <div class="form-grid">
          <label class="field">
            <span>{{ editorState.entityType === 'skill' ? 'Skill Key' : 'Key' }}</span>
            <input v-model.trim="editorState.form.key" type="text" placeholder="请输入唯一 Key" />
          </label>

          <label class="field">
            <span>{{ editorState.entityType === 'skill' ? 'Skill 名称' : '名称' }}</span>
            <input v-model.trim="editorState.form.name" type="text" placeholder="请输入名称" />
          </label>

          <label v-if="editorState.entityType === 'node'" class="field">
            <span>节点类型</span>
            <input v-model.trim="editorState.form.type" type="text" placeholder="例如：SQL 生成" />
          </label>

          <label v-if="editorState.entityType === 'config'" class="field">
            <span>配置类型</span>
            <input v-model.trim="editorState.form.type" type="text" placeholder="例如：提示消息 / 输出结构" />
          </label>

          <label v-if="editorState.entityType === 'node'" class="field">
            <span>执行方式</span>
            <select v-model="editorState.form.mode">
              <option value="串行">串行</option>
              <option value="并行/串行">并行/串行</option>
              <option value="回跳控制">回跳控制</option>
            </select>
          </label>

          <label v-if="editorState.entityType === 'skill'" class="field">
            <span>执行阶段</span>
            <select v-model="editorState.form.phase">
              <option value="默认">默认</option>
              <option value="BEFORE_EXECUTE">BEFORE_EXECUTE</option>
              <option value="AFTER_EXECUTE">AFTER_EXECUTE</option>
              <option value="REVIEW_OUTPUT">REVIEW_OUTPUT</option>
            </select>
          </label>

          <label class="field">
            <span>{{ editorState.entityType === 'skill' ? '状态 / 挂载态' : '状态' }}</span>
            <select v-model="editorState.form.status">
              <option value="启用">启用</option>
              <option value="停用">停用</option>
              <option value="已挂接">已挂接</option>
              <option value="未挂接">未挂接</option>
              <option value="可扩展">可扩展</option>
              <option value="规划中">规划中</option>
              <option value="待补充">待补充</option>
            </select>
          </label>

          <label class="field field-full">
            <span>描述</span>
            <textarea v-model.trim="editorState.form.summary" rows="5" placeholder="请输入描述信息" />
          </label>
        </div>

        <div class="panel-actions">
          <button type="button" class="ghost-action" @click="closeEditor">取消</button>
          <button type="button" class="mini-action primary" @click="submitEditor">保存</button>
        </div>
      </section>
    </div>

    <div v-if="detailState.visible" class="floating-mask" @click.self="closeDetail">
      <section class="floating-panel floating-panel-detail">
        <div class="floating-head">
          <div>
            <p class="eyebrow">{{ detailState.entityType === 'config' ? '配置详情' : 'Skill 详情' }}</p>
            <h3>{{ detailState.title }}</h3>
          </div>
          <button type="button" class="ghost-action" @click="closeDetail">关闭</button>
        </div>

        <div class="detail-fields">
          <div v-for="field in detailState.fields" :key="field.label" class="detail-field-row">
            <span>{{ field.label }}</span>
            <strong>{{ field.value }}</strong>
          </div>
        </div>

        <div class="detail-summary-block">
          <span>描述</span>
          <p>{{ detailState.summary }}</p>
        </div>
      </section>
    </div>

    <div v-if="confirmState.visible" class="floating-mask" @click.self="closeConfirm">
      <section class="floating-panel floating-panel-confirm">
        <div class="floating-head">
          <div>
            <p class="eyebrow">删除确认</p>
            <h3>确认删除“{{ confirmState.title }}”</h3>
          </div>
        </div>
        <p class="confirm-copy">这只是本地页面态删除，不会请求后端接口。</p>
        <div class="panel-actions">
          <button type="button" class="ghost-action" @click="closeConfirm">取消</button>
          <button type="button" class="mini-action danger-fill" @click="confirmRemoveItem">确认删除</button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.ai-flow-detail-page {
  display: grid;
  grid-template-rows: auto 1fr;
  gap: 12px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.content-head h1,
.content-head p,
.detail-card h3,
.detail-card p,
.detail-empty h2,
.detail-empty p,
.detail-main h2,
.detail-main p {
  margin: 0;
}

.content-head h1 {
  font-size: 30px;
  line-height: 1.05;
}

.content-head-main {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.section-desc,
.detail-copy,
.detail-nodes,
.detail-card p,
.detail-empty p {
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
}

.detail-hero,
.detail-card,
.detail-empty {
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 18px;
  background: #fff;
}

.detail-hero {
  padding: 14px 16px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: start;
}

.detail-main {
  display: grid;
  gap: 6px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.detail-main h2 {
  color: #0f172a;
  font-size: 18px;
  line-height: 1.2;
}

.flow-status {
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
}

.flow-status.is-live {
  color: #166534;
  background: rgba(34, 197, 94, 0.12);
}

.flow-status.is-draft {
  color: #92400e;
  background: rgba(245, 158, 11, 0.14);
}

.detail-nodes span {
  color: #334155;
  font-weight: 600;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: end;
}

.flow-tag {
  padding: 3px 8px;
  border-radius: 999px;
  border: 1px solid rgba(191, 219, 254, 0.95);
  background: rgba(239, 246, 255, 0.9);
  color: #1d4ed8;
  font-size: 10px;
  font-weight: 600;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.78fr) minmax(0, 1.22fr);
  gap: 10px;
  align-items: stretch;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.detail-card {
  padding: 12px 14px;
  display: grid;
  gap: 5px;
  min-height: 0;
  align-content: start;
}

.detail-side-column {
  display: grid;
  grid-column: 2 / 3;
  grid-row: 1 / span 2;
  grid-template-rows: 15fr 40fr 40fr;
  gap: 2.5%;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.detail-side-column > .detail-card {
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.detail-card-wide {
  grid-column: 1 / 2;
  grid-row: 1 / span 2;
  align-content: start;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.detail-card h3 {
  color: #0f172a;
  font-size: 15px;
  line-height: 1.25;
}

.detail-card-scroll-section {
  grid-template-rows: auto auto 1fr;
}

.section-head {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 10px;
}

.section-desc-inline {
  color: #475569;
  font-size: 12px;
  line-height: 1.45;
}

.node-list {
  display: grid;
  gap: 8px;
}

.node-row {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  background: rgba(248, 250, 252, 0.92);
  cursor: pointer;
}

.node-row.active {
  border-color: rgba(37, 99, 235, 0.28);
  background: rgba(239, 246, 255, 0.95);
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.08);
}

.node-index {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(37, 99, 235, 0.08);
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.node-main {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.node-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.node-title-row strong {
  color: #0f172a;
  font-size: 13px;
  line-height: 1.25;
}

.node-type {
  padding: 2px 7px;
  border-radius: 999px;
  background: rgba(239, 246, 255, 0.9);
  color: #1d4ed8;
  font-size: 10px;
  font-weight: 600;
}

.node-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  color: #64748b;
  font-size: 11px;
  line-height: 1.4;
}

.node-actions {
  display: grid;
  grid-template-columns: auto auto auto;
  gap: 6px;
  justify-items: end;
  align-items: center;
}

.node-move-actions {
  display: grid;
  gap: 4px;
}

.mini-action {
  border: 1px solid rgba(37, 99, 235, 0.14);
  border-radius: 10px;
  background: #fff;
  color: #2563eb;
  font-size: 11px;
  font-weight: 600;
  padding: 6px 9px;
  cursor: pointer;
}

.mini-action:disabled,
.ghost-action:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.mini-action.primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.mini-action.icon {
  min-width: 34px;
  padding: 6px 0;
  font-size: 12px;
  line-height: 1;
}

.detail-list {
  margin: 0;
  padding-left: 16px;
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
}

.detail-list li + li {
  margin-top: 4px;
}

.config-list {
  display: grid;
  gap: 8px;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  align-content: start;
}

.config-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  background: rgba(248, 250, 252, 0.92);
}

.list-empty {
  border: 1px dashed rgba(191, 219, 254, 0.95);
  border-radius: 14px;
  padding: 16px 14px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
  background: rgba(248, 250, 252, 0.88);
}

.config-main {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.config-main p {
  margin: 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.45;
}

.config-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.config-title-row strong {
  color: #0f172a;
  font-size: 13px;
  line-height: 1.25;
}

.config-type,
.config-status {
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
}

.config-type {
  background: rgba(239, 246, 255, 0.9);
  color: #1d4ed8;
}

.config-status {
  background: rgba(34, 197, 94, 0.12);
  color: #166534;
}

.config-status.draft {
  background: rgba(245, 158, 11, 0.14);
  color: #92400e;
}

.config-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: end;
}

.mini-action.danger {
  color: #dc2626;
  border-color: rgba(220, 38, 38, 0.16);
}

.mini-action.danger-fill {
  background: #dc2626;
  border-color: #dc2626;
  color: #fff;
}


.back-link {
  flex: none;
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 12px;
  background: #fff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
  padding: 8px 12px;
  cursor: pointer;
}

.ghost-action {
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 10px;
  background: #fff;
  color: #334155;
  font-size: 12px;
  font-weight: 600;
  padding: 8px 12px;
  cursor: pointer;
}

.toast {
  position: fixed;
  top: 84px;
  right: 24px;
  z-index: 50;
  padding: 11px 14px;
  border-radius: 12px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  background: #fff;
  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.12);
  color: #0f172a;
  font-size: 12px;
  font-weight: 600;
}

.toast.success {
  color: #166534;
}

.toast.warn {
  color: #92400e;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.floating-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  background: rgba(15, 23, 42, 0.22);
  display: grid;
  place-items: center;
  padding: 24px;
}

.floating-panel {
  width: min(620px, calc(100vw - 48px));
  max-height: calc(100vh - 64px);
  overflow: auto;
  border-radius: 20px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  background: #fff;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.18);
  padding: 18px;
  display: grid;
  gap: 16px;
}

.floating-panel-detail {
  width: min(520px, calc(100vw - 48px));
}

.floating-panel-confirm {
  width: min(420px, calc(100vw - 48px));
}

.floating-head {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 12px;
}

.floating-head h3 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.field {
  display: grid;
  gap: 6px;
}

.field span,
.detail-summary-block span,
.detail-field-row span {
  color: #475569;
  font-size: 12px;
  font-weight: 600;
}

.field input,
.field select,
.field textarea {
  width: 100%;
  border: 1px solid rgba(203, 213, 225, 0.95);
  border-radius: 12px;
  padding: 10px 12px;
  box-sizing: border-box;
  font: inherit;
  color: #0f172a;
  background: #fff;
  outline: none;
}

.field textarea {
  resize: vertical;
  min-height: 108px;
}

.field-full {
  grid-column: 1 / -1;
}

.panel-actions {
  display: flex;
  justify-content: end;
  gap: 10px;
}

.detail-fields {
  display: grid;
  gap: 10px;
}

.detail-field-row {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.92);
  border: 1px solid rgba(226, 232, 240, 0.95);
}

.detail-field-row strong {
  color: #0f172a;
  font-size: 13px;
  line-height: 1.4;
}

.detail-summary-block {
  display: grid;
  gap: 8px;
  padding: 12px;
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.92);
  border: 1px solid rgba(226, 232, 240, 0.95);
}

.detail-summary-block p {
  margin: 0;
  color: #334155;
}

.confirm-copy {
  margin: 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.55;
}

.detail-empty {
  padding: 20px;
  display: grid;
  gap: 8px;
}

@media (max-width: 1100px) {
  .detail-hero,
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .content-head-main {
    flex-direction: column;
    align-items: start;
  }

  .detail-card-wide {
    grid-column: auto;
    grid-row: auto;
  }

  .detail-tags {
    justify-content: start;
  }

  .node-row {
    grid-template-columns: 1fr;
  }

  .node-actions {
    grid-template-columns: repeat(3, auto);
    justify-items: start;
  }

  .config-row {
    grid-template-columns: 1fr;
  }

  .config-actions {
    justify-content: start;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
